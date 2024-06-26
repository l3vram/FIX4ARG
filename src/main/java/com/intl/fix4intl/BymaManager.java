/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intl.fix4intl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.intl.fix4intl.DBController.QuotationsBymaJpaController;
import com.intl.fix4intl.DBController.QuotationsJpaController;
import com.intl.fix4intl.Model.QuotationsByma;
import com.intl.fix4intl.Observable.ObservableQuotations;
import com.intl.fix4intl.Observable.OrderObservable;
import com.intl.fix4intl.RestOrdersGson.RestOrderService;
import quickfix.Message;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix50sp2.*;
import quickfix.fix50sp2.component.SecListGrp;

import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 *
 * @author yosbel
 */
public class BymaManager extends Manager {

    private final List<MarketSecurityListRequestInfo> marketInfoList = new LinkedList();
    public final List<Instrument> instruments = new ArrayList<>();
    private AtomicInteger countHeartBeat = new AtomicInteger(0);
    private static Instrument instrument;
    private static Message message;

    public BymaManager(OrderTableModel orderTableModel,
                       ExecutionTableModel executionTableModel,
                       InstrumentTableModel instrumentTableModel, OrderObservable orderObservable, ObservableQuotations observableQuotations, RestOrderService restService, QuotationsJpaController con) {
        super(orderTableModel, executionTableModel, instrumentTableModel, orderObservable, observableQuotations, restService, con);
    }

    @Override
     public void sendOrder(Order order) throws FieldNotFound {
        Instrument in = this.getInstrument(order.getSymbol(), this.getInstruments());
        if (in != null) {
            NewOrderSingle newOrderSingle = new NewOrderSingle(
                    new ClOrdID(order.getID()), sideToFIXSide(order.getSide()),
                    new TransactTime(), typeToFIXType(order.getType()));
            newOrderSingle.set(new Currency(in.getCurrency()));
            newOrderSingle.setField(new Account(order.getAccount()));
            newOrderSingle.setField(setTypeToFIXSetType(order.getSetType()));
            newOrderSingle.setField(new SecurityExchange("XMEV"));
            newOrderSingle.set(new OrderQty(order.getQuantity()));
            newOrderSingle.set(new Symbol(in.getAbreviatura()));
            newOrderSingle.getHeader().setField(new StringField(DeliverToCompID.FIELD, "FGW"));
            newOrderSingle.setField(new SecurityType(in.getSecurityType()));
            newOrderSingle.setField(new ExpireDate("20191210"));

            NewOrderSingle.NoPartyIDs noPartyIDs = new NewOrderSingle.NoPartyIDs();
            noPartyIDs.setField(new PartyIDSource(PartyIDSource.PROPRIETARY_CUSTOM_CODE));
            noPartyIDs.setField(new PartyID("dmax240a"));
            noPartyIDs.setField(new PartyRole(PartyRole.TRADER_MNEMONIC));
            newOrderSingle.addGroup(noPartyIDs);
            send(populateOrder(order, newOrderSingle), order.getSessionID());

        } else {
            System.out.println("Instrument NOT FOUND");
        }
    }
    @Override
     public void cancel(Order order) {
        String id = order.generateID();
        ClOrdID clorId = new ClOrdID(id);
        Side sideToFixSide = sideToFIXSide(order.getSide());
        OrderCancelRequest message = new OrderCancelRequest(clorId, sideToFixSide, new TransactTime());

        message.getHeader().setField(new StringField(DeliverToCompID.FIELD, "FGW"));
        message.setField(new OrderQty(order.getQuantity()));

        orderTableModel.addID(order, id);
        send(message, order.getSessionID());

    }
     
    @Override
     public void replace(Order order, Order newOrder) {
        ClOrdID clorId = new ClOrdID(newOrder.getID());
        Side side = sideToFIXSide(order.getSide());
        OrdType ordType = typeToFIXType(order.getType());
        OrderCancelReplaceRequest message = new OrderCancelReplaceRequest(clorId, side, new TransactTime(), ordType);
        message.getHeader().setField(new StringField(DeliverToCompID.FIELD, "FGW"));
        this.orderTableModel.addID(order, newOrder.getID());
        Message populateCancelReplace = populateCancelReplace(order, newOrder, message);
        send(populateCancelReplace, order.getSessionID());
    }
    
    
    @Override
    public void fillMarketSecurityListRequestInfo(Message message, SessionID sessionID) throws FieldNotFound, SessionNotFound {
        System.out.println("tradig status BYMA --> " + message);
        if (message.getField(new TradSesStatus()).valueEquals(TradSesStatus.OPEN)) {

            List<MarketSecurityListRequestInfo> marketListTemp = marketInfoList.stream().filter(m -> m.getSessionID().equals(sessionID)).collect(Collectors.toList());
            if (marketListTemp.isEmpty()) {
                List<MarketSegmentID> marketSegmentIDList = new LinkedList<>();
                marketSegmentIDList.add(new MarketSegmentID(message.getString(MarketSegmentID.FIELD)));
                MarketSecurityListRequestInfo marketInfoTest = new MarketSecurityListRequestInfo(sessionID, marketSegmentIDList);
                marketInfoList.add(marketInfoTest);
            } else {
                marketInfoList.stream().filter(m -> m.getSessionID().equals(sessionID)).collect(Collectors.toList())
                        .get(0)
                        .getMarketSegmentID().add(new MarketSegmentID(message.getString(MarketSegmentID.FIELD)));
            }

            requestSecurityList(message, sessionID);
        }

    }

    @Override
    public void requestSecurityList(Message message, SessionID sessionID) throws SessionNotFound {
        DateFormat hourdateFormat = new SimpleDateFormat("ddMMyyyyHHmmss");
        Date date = new Date();
        String reqId = "SecListReq-INTL-" + hourdateFormat.format(date);
        SecurityListRequest securityListRequest = new SecurityListRequest();
        securityListRequest.setField(new SecurityReqID(reqId));
        securityListRequest.setField(new SecurityListRequestType(SecurityListRequestType.ALL_SECURITIES));
        securityListRequest.setField(new SecurityListType(SecurityListType.MARKET_MARKET_SEGMENT_LIST));
        securityListRequest.setField(new StringField(DeliverToCompID.FIELD, "FGW"));
        securityListRequest.setField(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT));
        Session.sendToTarget(securityListRequest, sessionID);
    }

    @Override
    public void fillInstrumentList(Message message, SessionID sessionID) throws FieldNotFound {

        int totElements = message.getInt(TotNoRelatedSym.FIELD);
        SecListGrp.NoRelatedSym noRelatedSym = new SecListGrp.NoRelatedSym();
        String securityID = message.getString(SecurityReqID.FIELD);
       
        for (int i = 1; i <= totElements; i++) {
            Instrument instrument = new Instrument(message.getGroup(i, noRelatedSym), sessionID,securityID);
            instruments.add(instrument);           
        }
        instrumentTableModel.update(instruments);

    }

    @Override
    public void createMarketDataRequest(int dUpdateType, SessionID sessionID) throws SessionNotFound {
        for (Instrument instrument : instruments) {
            MarketDataRequest mdr = comunCreateMarketDataRequest(instrument, sessionID);
            mdr.setField(new MDUpdateType(MDUpdateType.INCREMENTAL_REFRESH));
            mdr.setField(new MarketDepth(5));
            mdr.getHeader().setField(new StringField(DeliverToCompID.FIELD, "FGW"));
            
            Session.sendToTarget(mdr, instrument.getSessionID());

            this.countHeartBeat = new AtomicInteger(1);

        }
    }

    @Override
    public void fillCotizaciones(MarketDataSnapshotFullRefresh message, SessionID sessionID) throws FieldNotFound, InterruptedException, InvocationTargetException {
        MarketDataSnapshotFullRefresh.NoMDEntries group = new MarketDataSnapshotFullRefresh.NoMDEntries();
        int length = message.getNoMDEntries().getValue();

        Symbol symbol = message.getSymbol();
        instrument = getInstrument(symbol.getValue(), instruments);
        int bookType = message.getMDBookType().getValue();

        if (bookType == MDBookType.PRICE_DEPTH) {
            if (instrument == null) {
                System.out.println("El Symbol no esta en la lista: " + symbol.getValue());
            } else {

                for (int i = 1; i <= length; i++) {
                    String securityID = message.getSecurityID().getValue();

                    // Obtengo el subgrupo (imbalance, bid, offer, trade, etc)                      
                    Group MDFullGrp = message.getGroup(i, group);                    
                    instrument.setValues(MDFullGrp,securityID);
                }
            }
        } else {
            System.out.println("Market Data Full Refresh Not Price Depth: " + bookType);
        }
        if (instrument != null) {
            insertQuotesByma();
        }
        instrumentTableModel.update(instruments);
    }

    @Override
    public void fillCotizaciones(MarketDataIncrementalRefresh message, SessionID sessionID) throws FieldNotFound, InterruptedException, InvocationTargetException {

        MarketDataIncrementalRefresh.NoMDEntries group = new MarketDataIncrementalRefresh.NoMDEntries();
        int length = message.getNoMDEntries().getValue();
        //Instrument instrument = null;
        for (int i = 1; i <= length; i++) {

            Group MDFullGrp = message.getGroup(i, new MarketDataIncrementalRefresh.NoMDEntries());
            String symbolStr = MDFullGrp.getField(new Symbol()).getValue();

            instrument = getInstrument(symbolStr, instruments);
            if (instrument != null) {
                String securityID = MDFullGrp.getField(new SecurityID()).getValue();
                instrument.setValues(MDFullGrp,securityID);
            }
        }
        if (instrument != null) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
             executor.submit(() -> {
                 if(!Thread.interrupted()){
                     insertQuotesByma();
                 }
            });
            
        }
        instrumentTableModel.update(instruments);

    }

    private static void insertQuotesByma() {

        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);

        try {
            QuotationsByma q = new QuotationsByma();
            q.setUltimoOperado(instrument.getUltimoOperado());
            q.setVariacion(instrument.getVariacion());
            q.setMoneda(instrument.getCurrency());
            q.setSecurityType(instrument.getSecurityType());
            q.setTrades(mapper.writeValueAsString(instrument.getTrades()));
            if (instrument.getSecurityID() != null) {
                q.setSymbol(instrument.getSecurityID());
            }

            q.setFecha(new Timestamp(new Date().getTime()));
            q.setFixMessage(message.toRawString());
            q.setVencimiento(instrument.getSettlType());
            String data = mapper.writeValueAsString(instrument);
            q.setData(data);
            QuotationsBymaJpaController c = new QuotationsBymaJpaController();
            c.createIfnotExist(q);
        } catch (FieldNotFound | JsonProcessingException ex) {
        } catch (NullPointerException ex) {
            String x = ex.getMessage();
        } catch (Exception ex) {
        }

    }

    @Override
    public  List<Instrument> getInstruments() {
        return this.instruments;
    }

}
