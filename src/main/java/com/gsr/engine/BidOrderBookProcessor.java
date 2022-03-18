package com.gsr.engine;

import com.gsr.analytics.Request;
import com.gsr.data.*;
import com.gsr.feed.ObjectPool;

import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Implementation of the Bid side of an order book
 */
public class BidOrderBookProcessor extends OrderBookProcessor{

    public BidOrderBookProcessor(CcyPair pair, ObjectPool<Message> messageObjectPool, ConcurrentLinkedQueue<Message> distributorInboundQueue, ConcurrentLinkedQueue<Request> requestQueue, ConcurrentLinkedQueue<Request> responseQueue) {
        super(pair,  messageObjectPool, distributorInboundQueue, requestQueue,responseQueue );
    }

    @Override
    protected Side getSide() {
        return Side.Bid;
    }

    @Override
    public double calculateAveragePrice(int levels) {
        int ptr = 0;
        PriceLevel currLevel = topOfBook.get();

        if(currLevel == null){
            return 0;
        }

        long totalPrice= 0;
        while(ptr < levels && currLevel != null){
            totalPrice += currLevel.getPrice();
            ptr +=1;
            currLevel = currLevel.getNextLower();
        }

        return (double) totalPrice / (ptr * 100);
    }

    @Override
    public long calculateQtyOverLevels(int levels) {
        int ptr = 0;
        PriceLevel currLevel = topOfBook.get();
        long totalQty= 0;
        while(ptr < levels && currLevel != null){
            totalQty += currLevel.getQuantity();
            ptr +=1;
            currLevel = currLevel.getNextLower();
        }

        return totalQty;
    }

    @Override
    public double calculateVwapOverLevels(int levels) {
        int ptr = 0;
        PriceLevel currLevel = topOfBook.get();
        double totalPriceWeight= 0;
        while(ptr < levels && currLevel != null){
            totalPriceWeight += (currLevel.getPrice() * currLevel.getQuantity());
            ptr +=1;
            currLevel = currLevel.getNextLower();
        }

        return  totalPriceWeight / (calculateQtyOverLevels(levels) *100);
    }

    @Override
    protected double getTopOfBookPrice() {
        return topOfBook.get() == null ? 0 : (double)topOfBook.get().getPrice() / 100;
    }


    /**
     * Limits are ordered in a sorted double linked list. When a new limit arrives, we traverse the list and insert
     * at the appropriate spot
     * @param priceToInsert new price level to be added
     * @param currentPriceLevel price level to compare to, normally start at top of book
     */
    protected void insertInChain(PriceLevel priceToInsert, PriceLevel currentPriceLevel) {
        if (priceToInsert.getPrice() > currentPriceLevel.getPrice()) {
            PriceLevel limitAboveCurrent = currentPriceLevel.getNextHigher();
            if (limitAboveCurrent == null) {
                //We're inserting a new best price
                currentPriceLevel.setNextHigher(priceToInsert);
                priceToInsert.setNextLower(currentPriceLevel);
                topOfBook.set(priceToInsert);
            }else{
                //We're inserting a new price somewhere in the middle of the book
                limitAboveCurrent.setNextLower(priceToInsert);
                priceToInsert.setNextHigher(limitAboveCurrent);
                priceToInsert.setNextLower(currentPriceLevel);
                currentPriceLevel.setNextHigher(currentPriceLevel);
            }
            return;

        } else if (currentPriceLevel.getNextLower() == null) {
            //We're inserting a price at the bottom of the book
            currentPriceLevel.setNextLower(priceToInsert);
            priceToInsert.setNextHigher(currentPriceLevel);
            return;
        }
        //Recurse and step to the next limit in the book
        insertInChain(priceToInsert, currentPriceLevel.getNextLower());
    }
}
