package bguspl.set.ex;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 * 
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    // ================================FIELDS================================

    private final Dealer dealer;
    /**
     * The game environment object.
     */
    private final Env env;
    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The token counter of the player.
     */
    private final AtomicInteger tokenCounter;

    /**
     * The queue of key presses (slots) generated by the player.
     */
    private final BlockingQueue<Integer> slotQueue;

    /**
     * true iff we need to penalty the player
     */
    private final AtomicBoolean isPenaltyTime;

    /**
     * true iff we need to award the player a point
     */
    private final AtomicBoolean isPointTime;

    /**
     * true iff we found a set and it's time to check it
     */
    private final AtomicBoolean isSetTime;

   

    // ================================CONSTRUCTOR================================

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */

    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.score = 0;
        this.terminate = false;
        this.tokenCounter = new AtomicInteger(0);
        this.isPenaltyTime = new AtomicBoolean(false);
        this.isPointTime = new AtomicBoolean(false);
        this.isSetTime = new AtomicBoolean(false);
        this.slotQueue = new LinkedBlockingQueue<>(env.config.featureSize);
    }

    // ================================BASIC-METHODS================================

    public int getId() {return id;}
    public int score() {return score;}
    public void increaseToken() {tokenCounter.incrementAndGet();}
    public void decreaseToken() {tokenCounter.decrementAndGet();}
    public BlockingQueue<Integer> getslotQueue() {synchronized (slotQueue) {return this.slotQueue;}}

    // Game should be terminated
    public void terminate() {
        terminate = true;
    if (aiThread != null) {
        aiThread.interrupt();
        try {
            aiThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    if (playerThread != null) {
        playerThread.interrupt();
        try {
            playerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    } 
    // ================================THREAD-METHODS================================

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        // GAME START
        playerThread = Thread.currentThread();
        playerThread.setName("Player-" + id);
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        synchronized(dealer.getTerminateList()){
            dealer.getTerminateList().add(this);    
        }
        if (!human)
            createArtificialIntelligence();

        // MID GAME
        while (!terminate) {
            // Check if penalty
            if(isPenaltyTime.get()){this.doPenalty();}
            // Check if point
            if(isPointTime.get()){this.doPoint();}

            // Check if it's time to add a set
            if(isSetTime.get()){
                isSetTime.compareAndSet(true, false);
                synchronized(dealer.getPlayersRequestsQueue()){
                    dealer.getPlayersRequestsQueue().add(this);
                    dealer.getPlayersRequestsQueue().notifyAll();
                } 
            }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
           // try {
                while (!terminate) {
                    aiKeyPressed();
                    synchronized(this){
                        try{
                            wait(5); // Intervals between key presses.
                        }
                        catch (InterruptedException e) {
                            env.logger.warning("Thread interrupted while waiting.");
                            Thread.currentThread().interrupt(); // Restore interrupted status
                        }
                    }
                }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * To forbid players using keyPresses while there are no human players -> 2 methods: 1 for humans and 1 for AI.
     */
    public void keyPressed(int slot) {
        if (human) 
            processKeyPress(slot);
    }
    public void aiKeyPressed() {
        if (!human) {
            int slot = generateSlotKey();
            if(slot!=-1)
                processKeyPress(slot);
        }
    }
    public void processKeyPress(int slot){
        // Allow player to play only if he is not in penalty freeze, point freeze,
        if (!isPenaltyTime.get() && !isPointTime.get() && !isSetTime.get()) {
            synchronized(table){
                if (table.slotToCard[slot] != null && dealer.getGameStart()) {
                    try {
                        synchronized (slotQueue) {
                            // Key pressed on a place with a token - remove token
                            if (slotQueue.contains(slot)) {
                                slotQueue.remove(slot);
                                decreaseToken();
                                table.removeToken(id,slot);
                            // Key pressed once -  add token
                            } else if (slotQueue.remainingCapacity() > 0) {
                                slotQueue.add(slot);
                                increaseToken();
                                table.placeToken(id, slot);
                            }
                            // We got a set to check
                            if(slotQueue.remainingCapacity() == 0){
                                isSetTime.compareAndSet(false, true);
                            }
                        }
                    } catch (Exception e) {}
                }
            }
        }
    }
    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {  
        isPointTime.compareAndSet(false,true);
        score++;
        env.ui.setScore(id, score);

    }

    public void doPoint() {
        tokenCounter.set(0);
        try{
            int freeze = (int)env.config.pointFreezeMillis;
            // Freezing for 1 seconds at a time
            while(freeze >= 1000){
                env.ui.setFreeze(id, freeze);
                Thread.sleep(1000);
                freeze -= 1000;
            }
            // Freezing the remaining 0.x seconds because now (freeze < 1000 milliseconds)
            Thread.sleep(freeze);
            env.ui.setFreeze(id, 0);
        }catch(InterruptedException ignore){}
        isPointTime.compareAndSet(true,false);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        isPenaltyTime.compareAndSet(false, true);
        // Removing the tokens from the table
        synchronized(slotQueue){
            synchronized(table){
                while(!slotQueue.isEmpty()){
                    table.removeToken(id,slotQueue.poll());
                    decreaseToken();
                }
            }
        }
    }
    
    public void doPenalty() {
        try{
            int freeze = (int)env.config.penaltyFreezeMillis; 
            while(freeze >= 1000){
                env.ui.setFreeze(id, freeze);
                Thread.sleep(1000);
                freeze -= 1000;
            }
            // Freezing the remaining 0.x seconds because now (freeze < 1000 milliseconds)
            Thread.sleep(freeze);
            env.ui.setFreeze(id, 0);
        }catch(InterruptedException ignore){}
       isPenaltyTime.compareAndSet(true,false);
    }

    /**
     * @return the set the player created.
     */
    public int[] getSet() {
        synchronized (table) {
            synchronized(slotQueue){
                int[] set = new int[env.config.featureSize];
                if (slotQueue.remainingCapacity() == 0){
                    for (int i = 0; i < env.config.featureSize; i++) {
                            Integer slot = slotQueue.poll();
                            if (slot != null && slot >= 0 && slot < table.slotToCard.length && table.slotToCard[slot] != null) {
                                set[i] = table.slotToCard[slot];
                            }
                    }
                    for (int i = 0; i < env.config.featureSize && table.cardToSlot[set[i]] != null; i++)
                        slotQueue.add(table.cardToSlot[set[i]]);
                }
                return set;
            }
        }
    }

    /**
     * Generating slot keys based on the size of the deck
     * @return Slot key generated or -1 if we finished generating
     */
    private int generateSlotKey() {
        int slot=-1;
        Random random = new Random();
        synchronized(slotQueue){
            if(slotQueue.size()<env.config.featureSize){
                slot = random.nextInt(env.config.columns * env.config.rows);
            }
        }
        return slot;
    }
}