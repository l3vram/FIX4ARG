
package com.intl.fix4intl;
import java.util.HashMap;
import java.util.Map;

public class OrderTIF {
    static private Map<String, OrderTIF> known = new HashMap<String, OrderTIF>();
    static public final OrderTIF DAY = new OrderTIF("Day");
    static public final OrderTIF IOC = new OrderTIF("IOC");
    static public final OrderTIF OPG = new OrderTIF("OPG");
    static public final OrderTIF GTC = new OrderTIF("GTC");
    static public final OrderTIF GTD = new OrderTIF("GTD");
    static public final OrderTIF GTX = new OrderTIF("GTX");

    static private OrderTIF[] array = { DAY, IOC, OPG, GTC,GTD, GTX };

    private String name;

    public OrderTIF(String name) {
        this.name = name;
        synchronized(OrderTIF.class) {
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

    public static OrderTIF parse(String type)
    throws IllegalArgumentException {
        OrderTIF result = known.get(type);
        if(result == null) {
            throw new IllegalArgumentException
            ("OrderTIF:  " + type + " is unknown.");
        }
        return result;
    }
}
