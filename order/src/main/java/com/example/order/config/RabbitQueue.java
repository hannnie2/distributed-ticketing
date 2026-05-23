package com.example.order.config;

public class RabbitQueue {

    public static final String ORDER_EXCHANGE = "x.order";
    public static final String DEAD_LETTER_EXCHANGE = "x.deadLetter";

    public static final String UNPAID_ORDER_CHECK_QUEUE = "q.order.unpaid_order_check";
    public static final String ORDER_PENDING_CREATED_KEY = "order_pending_created";
    public static final String ORDER_EXPIRED_QUEUE = "q.order.order_expired";
    public static final String ORDER_EXPIRED_KEY = "order_expired";

    public static final String ORDER_CONFIRMATION_QUEUE = "q.order.order_confirmation";
    public static final String INVENTORY_DEDUCTED_KEY = "inventory_deducted";
    public static final String PAYMENT_SUCCEEDED_KEY = "payment_succeeded";
    public static final String INVENTORY_EXCHANGE = "x.inventory";
    public static final String PAYMENT_EXCHANGE = "x.payment";

    public static final String INVENTORY_DEDUCTION_FAILED_QUEUE = "q.order.inventory_deduction_failed";
    public static final String INVENTORY_DEDUCTION_FAILED_KEY = "inventory_deduction_failed";
    public static final String ORDER_CANCELLED_KEY = "order_cancelled";
    public static final String ORDER_CONFIRMED_KEY = "order_confirmed";
    public static final String REFUND_REQUIRED_KEY = "refund_required";
}