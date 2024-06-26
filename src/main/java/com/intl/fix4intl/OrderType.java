/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intl.fix4intl;
/**
 *
 * @author mar
 */

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OrderType {
    static private Map<String, OrderType> known = new HashMap<String, OrderType>();
    static public final OrderType MARKET = new OrderType("Market");
    static public final OrderType LIMIT = new OrderType("Limit");
    static public final OrderType STOP = new OrderType("Stop");
    static public final OrderType STOP_LIMIT = new OrderType("Stop Limit");
    private String name;

    static private OrderType[] array = { MARKET, LIMIT, STOP, STOP_LIMIT };

    public OrderType(String name) {
        this.name = name;
        synchronized(OrderType.class) {
            known.put(name, this);
        }
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return name;
    }

    static public Object[] toArray() {
        return array;
    }

    public static OrderType parse(String type) throws IllegalArgumentException {
        OrderType result = known.get(type);
        if(result == null) {
            throw new IllegalArgumentException
            ("OrderType:  " + type + " is unknown.");
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderType orderType = (OrderType) o;
        return Objects.equals(name, orderType.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
