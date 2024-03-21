package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

//================================FIELDS================================

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    
    /**
     * Saves the tokens that are on the grid (FIFO)
     */
    private final ConcurrentLinkedQueue<Integer>[] gridTokenQueue;

// ================================CONSTRUCTORS================================
    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.gridTokenQueue = new ConcurrentLinkedQueue[env.config.tableSize];
        for (int i = 0; i < env.config.tableSize; i++) {
            gridTokenQueue[i] = new ConcurrentLinkedQueue<>();
        }
    }
    
    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

// ================================METHODS================================

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public synchronized int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card,slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        synchronized(slotToCard){
            synchronized(cardToSlot){
                if(slotToCard[slot] != null){
                    int card = slotToCard[slot];
                    slotToCard[slot]= null; 
                    cardToSlot[card]= null;
                    env.ui.removeCard(slot);
                }
            }
        }
        //Remove the tokens set by UI.
        synchronized(gridTokenQueue){
            ConcurrentLinkedQueue<Integer> playerIds = gridTokenQueue[slot];
            for (Integer playerId : playerIds) {
                env.ui.removeToken(playerId, slot);
            }
            playerIds.clear();
        }
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        synchronized(gridTokenQueue){
            env.ui.placeToken(player,slot);
            gridTokenQueue[slot].add(player);
        }       
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        synchronized(gridTokenQueue){
            if(slotToCard[slot]!=null && searchToken(player, slot)){
                env.ui.removeToken(player,slot);
                return gridTokenQueue[slot].remove(player);
            }
        }
        return false; 
    }

    /*
     * Search for a token of a player in a slot.
     */
    private boolean searchToken(int player, int slot){
        synchronized(gridTokenQueue){return gridTokenQueue[slot].contains(player);}}

    /**
     * For placing cards in random order on table
     * @return the empty slot
     */
    public synchronized int getEmptySlot(){
        List<Integer> slices = new ArrayList<>();
        for (int i = 0; i < slotToCard.length; i++) {slices.add(i);}
        
        Collections.shuffle(slices);
        for (int i : slices) {
            if (slotToCard[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * For removing cards in random order from table
     * @return the full slot
     */
    public synchronized int getFullSlots(){
        List<Integer> slices = new ArrayList<>();
        for (int i = 0; i < slotToCard.length; i++) {slices.add(i);}
        
        Collections.shuffle(slices);
        for (int i : slices) {
            if (slotToCard[i] != null) {
                return i;
            }
        }
        return -1;
    }

    
    /**
     * Get the card in a slot.
     * @return intArray - array of the card in the slot.
     */
    public synchronized List<Integer> getSlotTocardList() {
        List<Integer> slotToCardList = new ArrayList<>();
        if (slotToCard != null) {
        for (Integer cardId : slotToCard) {
            if (cardId != null) {
                slotToCardList.add(cardId);
            }
        }
    }
        return slotToCardList;
    }

    public synchronized ConcurrentLinkedQueue<Integer>[] getGridTokenQueue(){return this.gridTokenQueue;}
}