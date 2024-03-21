package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    // ================================FIELDS================================

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The list of players.
     */
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;

    /**
    * A flag indicating whether the game has started and finished putting all the cards
    */
    private boolean gameStart ;

    /**
    * A flag indicating whether the time needs to be red or black.
    */
    private boolean warning;

    /**
     * The queue of players requests to check set. (FIFO)
     */
    private final BlockingQueue<Player> playersRequestsQueue;

    /**
    * An Array for all the players threads
    */
    private final Thread[] playerThreads;

    /**
     * List of players that should be terminated by the order.
     */
    private final List<Player> terminateList;

    // ================================CONSTRUCTORS================================

    /**
     * 
     * @param env - the environment object
     * @param table - the table object
     * @param players - the players array
     */
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.terminate = false;
        this.warning=false;
        this.gameStart=false;
        this.playersRequestsQueue = new LinkedBlockingQueue<>();
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playerThreads = new Thread[players.length];
        terminateList = new ArrayList<>();
    }

    // ================================BASIC-METHODS================================

    public boolean getGameStart() {return gameStart;}
    public synchronized BlockingQueue<Player> getPlayersRequestsQueue() {return playersRequestsQueue;}
    /**
     * @return true iff the game should be finished.
     * Check if the game should be terminated or the game end conditions are met.
     */
    private boolean shouldFinish() {return terminate || env.util.findSets(deck, 1).size() == 0 ;}
    public List<Player> getTerminateList() {return terminateList;}

    // ================================THREAD-METHODS================================
    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        // GAME START
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        placeCardsOnTable();
        // Starting all the players threads
        synchronized (playerThreads) {
            for (Player p : players) {
                Thread playerThread = new Thread(p);
                playerThreads[p.getId()] = playerThread;
                playerThread.start();
            }
        }
        // MID GAME
        while (!shouldFinish()) {
            placeCardsOnTable();
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            warning=false;
            updateTimerDisplay(false);
            timerLoop();
            gameStart = false;
            removeAllCardsFromTable();
        }

        // END GAME
        announceWinners();
        terminate();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     * @param reset - .
     */
    private void updateTimerDisplay(boolean reset) {
        // TIME'S OUT
        if(System.currentTimeMillis() >= reshuffleTime ){
            warning = false;
        }
        // WARNING TIME
        if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
            warning = true;
        }
        // CONTINUE
        env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), warning);
    } 

    /**
     * Reshuffle the deck and place new cards on the table.
     */
    public void reShuffle() {
        gameStart = true;
        removeAllCardsFromTable();
        Collections.shuffle(deck);
        placeCardsOnTable();
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis+1000;
        warning = false;
    }

    /**
     * Checks if there's a possible set on board.
     * @return true iff there's no set on board.
     */
    private boolean shouldReShuffle() {
        List<Integer> tableCards = table.getSlotTocardList();
        return env.util.findSets(tableCards, 1).isEmpty();
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        if(!deck.isEmpty()){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis+1000; // +1000 to avoid the time until the timer display is working to the input.
        }
        else{
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis; // We don't try to place cards anymore therefor - saved 1 second.
        }
        while (!terminate && System.currentTimeMillis() < reshuffleTime) { 
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(warning);
            removeCardsFromTable();
            if(!deck.isEmpty()){
                placeCardsOnTable();
            }
        }
    }

    /**
     * Check cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        Player player;
        // Get the player that put set first
        synchronized (playersRequestsQueue) {
            player = playersRequestsQueue.poll();
        }
        // Get the player set -> check it is really the player set
        if (player != null) {
            int[] set = player.getSet();
            boolean mySet = true;
            synchronized(player.getslotQueue()){
                synchronized(table){
                    for (int j : set) {
                        if (!player.getslotQueue().contains(table.cardToSlot[j]) || player.getslotQueue().size() != env.config.featureSize) {
                            mySet = false;
                        }
                    }
                }
            }
            // Check if valid set -> point \ penalty
            if (set != null && mySet) {
                if (env.util.testSet(set)) {
                    // Clear tokens of the player - by player class, increment score and clear slotQ of the player.
                    // Remove cards from the table because they form a set + all tokens of all the other players that are on the cards.
                    env.logger.info("Player " + player.getId() + "checking set" + Arrays.toString(set));
                    synchronized (table) {
                        for (Player player1 : players) {
                            synchronized (player1.getslotQueue()) {
                                for (int j : set) {
                                    player1.getslotQueue().remove(table.cardToSlot[j]);
                                }
                            }
                        }
                        for (int j : set) {
                            if (table.cardToSlot[j] != null)
                                table.removeCard(table.cardToSlot[j]);
                        }
                    }
                    synchronized(player) {
                        player.point();
                    }
                    // Reset Timer when set is valid.
                    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis+1000;
                    warning = false;
                    
            // The set isn't good.
                } else {
                    synchronized(player){
                        player.penalty();
                    }
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Random random = new Random();
        synchronized (table) {
            env.logger.info("Placing cards on the table.");
            // Place a new card if you have cards left in deck and there's an empty slot on the table.
            while (table.getEmptySlot() != -1 && !deck.isEmpty()) {
                int randomCardIndex = random.nextInt(deck.size());
                table.placeCard(deck.get(randomCardIndex), table.getEmptySlot());
                deck.remove(randomCardIndex);
            }
            // If we should reshuffle hold all the players -> gameStart = false.
            while(shouldReShuffle() && !deck.isEmpty()){
                gameStart = false;
                reShuffle();
            }
        }
        gameStart = true;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
         synchronized (playersRequestsQueue) {
            try {
                if(reshuffleTime - System.currentTimeMillis() >= env.config.turnTimeoutWarningMillis)
                    playersRequestsQueue.wait(100); // Dealer wait until timeoutMillis or notify\notifyAll.
            }
            catch (InterruptedException e) {}
         }
    }

    /**
     * Removes all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table) {
                env.logger.info("Removing cards from the table.");
                int fullSlot = table.getFullSlots();
                while (fullSlot != -1) {
                    // Add the card back to the deck
                    deck.add(table.slotToCard[fullSlot]);
                    // Remove tokens from each player Queue
                    for(int i = 0; i < table.getGridTokenQueue().length; i++){
                        for(Player player : players){
                            player.getslotQueue().remove(i);
                        }
                    }
                    // Remove the card from the table and the tokens on it(UI)
                    table.removeCard(fullSlot);
                    fullSlot = table.getFullSlots();
                }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0;
        List<Player> winnersList = new ArrayList<>();
        // Get Max Score
        for (Player player : players) {
            if (player.score() >= maxScore) {
                maxScore = player.score();
            }
        }
        // Get all the players with that max Score
        for (Player player : players) {
            if (player.score() == maxScore) {
                winnersList.add(player);
            }
        }
        // Put them in array for the UI.
        int[] winnersIds = new int[winnersList.size()];
        for (int i = 0; i < winnersIds.length; i++) {
            winnersIds[i] = winnersList.get(i).getId();
        }
        env.ui.announceWinner(winnersIds);
    }

    /*
     * Called when the game should be terminated.
     */
    public void terminate() {
        int size = terminateList.size();
        for(int i=size-1;i>=0;i--){
            terminateList.get(i).terminate();
        }
        terminate = true;
    }
}