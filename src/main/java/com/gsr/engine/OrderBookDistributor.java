package com.gsr.engine;

import com.gsr.data.Message;
import com.gsr.feed.ObjectPool;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Class responsible for unpacking instructions and sending them for processing to the correct threads.
 * One thread running for each Currency pairs side of book.
 * Non blocking thread communication is provided via ConcurrentLinkedQueues.
 */
public class OrderBookDistributor {


    private volatile boolean runningFlag = true;
    private final ConcurrentLinkedQueue<Message> incomingMessageQueue;
    private final ConcurrentLinkedQueue<Message> btcUsdOfferBookQueue;
    private final ConcurrentLinkedQueue<Message> btcUsdBidBookQueue;
    private final ConcurrentLinkedQueue<Message> ethUsdOfferBookQueue;
    private final ConcurrentLinkedQueue<Message> ethUsdBidBookQueue;
    private final ConcurrentLinkedQueue<Message> solUsdOfferBookQueue;
    private final ConcurrentLinkedQueue<Message> solUsdBidBookQueue;
    private final ObjectPool<Message> messagePool;

    public OrderBookDistributor(ConcurrentLinkedQueue<Message> inboundQueue, List<ConcurrentLinkedQueue<Message>> engineQueues, ObjectPool<Message> messagePool) {

        this.messagePool = messagePool;

        incomingMessageQueue = inboundQueue;
        btcUsdOfferBookQueue = engineQueues.get(0);
        btcUsdBidBookQueue = engineQueues.get(1);
        ethUsdOfferBookQueue = engineQueues.get(2);
        ethUsdBidBookQueue = engineQueues.get(3);
        solUsdOfferBookQueue = engineQueues.get(4);
        solUsdBidBookQueue = engineQueues.get(5);

        Thread thread = new Thread(() -> {
            System.out.println("Order Book Distributor Running");

            while (runningFlag) {
                Message message = incomingMessageQueue.poll();
                if (message != null) {
                    processMessage(message);
                }
            }
        });

        thread.start();
    }

    /**
     * Helper method to decide which queue to send a particular request to.
     *
     * @param message
     */
    private void processMessage(Message message) {

        switch (message.getType()) {

            case AddOrUpdatePriceLevel:
                switch (message.getSide()) {
                    case Offer:
                        switch (message.getPair()) {
                            case ETHUSD:
                                ethUsdOfferBookQueue.add(message);
                                return;
                            case BTCUSD:
                                btcUsdOfferBookQueue.add(message);
                                return;
                            case SOLUSD:
                                solUsdOfferBookQueue.add(message);
                        }

                        break;
                    case Bid:
                        switch (message.getPair()) {
                            case ETHUSD:
                                ethUsdBidBookQueue.add(message);
                                return;
                            case BTCUSD:
                                btcUsdBidBookQueue.add(message);
                                return;
                            case SOLUSD:
                                solUsdBidBookQueue.add(message);
                        }
                        break;
                    default:
                        System.out.println("Unexpected Message Type which is not handled: " + message.getSide());
                        messagePool.returnObject(message);
                        return;
                }

            case RemovePriceLevel:
                //To avoid having to synchronize between threads. Send the same to all. Let them do their stuff.
                sendClonedMessage(btcUsdBidBookQueue, message);
                sendClonedMessage(btcUsdOfferBookQueue, message);
                sendClonedMessage(ethUsdBidBookQueue, message);
                sendClonedMessage(ethUsdOfferBookQueue, message);
                messagePool.returnObject(message);
        }
    }

    private void sendClonedMessage(ConcurrentLinkedQueue<Message> queue, Message message) {
        Message cloneMessage = messagePool.acquireObject();
        cloneMessage.populateFields(message);
        queue.add(cloneMessage);
    }

    public void shutdown() {
        System.out.println("Shuttingdown OrderBook Distributor");
        runningFlag = false;
    }
}
