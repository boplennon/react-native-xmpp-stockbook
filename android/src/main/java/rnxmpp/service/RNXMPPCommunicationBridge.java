package rnxmpp.service;

import android.support.annotation.Nullable;

import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;

import org.jivesoftware.smack.packet.IQ;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterGroup;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.PacketParserUtils;

import java.io.IOException;
import java.io.StringReader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.mam.element.MamElements.MamResultExtension;
import org.jivesoftware.smackx.mam.provider.MamResultProvider;

import rnxmpp.utils.Parser;

/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class RNXMPPCommunicationBridge implements XmppServiceListener {

    public static final String RNXMPP_ERROR =       "RNXMPPError";
    public static final String RNXMPP_LOGIN_ERROR = "RNXMPPLoginError";
    public static final String RNXMPP_MESSAGE =     "RNXMPPMessage";
    public static final String RNXMPP_ROSTER =      "RNXMPPRoster";
    public static final String RNXMPP_IQ =          "RNXMPPIQ";
    public static final String RNXMPP_PRESENCE =    "RNXMPPPresence";
    public static final String RNXMPP_CONNECT =     "RNXMPPConnect";
    public static final String RNXMPP_DISCONNECT =  "RNXMPPDisconnect";
    public static final String RNXMPP_LOGIN =       "RNXMPPLogin";
    private static final String ns = null;
    ReactContext reactContext;

    public RNXMPPCommunicationBridge(ReactContext reactContext) {
        this.reactContext = reactContext;
    }

    @Override
    public void onError(Exception e) {
        sendEvent(reactContext, RNXMPP_ERROR, e.getLocalizedMessage());
    }

    @Override
    public void onLoginError(String errorMessage) {
        sendEvent(reactContext, RNXMPP_LOGIN_ERROR, errorMessage);
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public String getId (String xml )throws XmlPullParserException, IOException
    {
        Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
        logger.log(Level.WARNING, "xml --->" + xml );
        String id = null;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if(name!=null) {
                    Log.d("ELE>>>>", name);
                    if (name.equals("stanza-id")) {
                        parser.require(XmlPullParser.START_TAG, ns, "stanza-id");
                        id = parser.getAttributeValue(null, "id");
                        break;
                    }
                }

            }



        } catch( XmlPullParserException  err) {
            logger.log(Level.SEVERE, "ERORORORORO --->", err );
        }
        return id;
    };

    @Override
    public void onLoginError(Exception e) {
        this.onLoginError(e.getLocalizedMessage());
    }

    @Override
    public void onMessage(Message message) {
        Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
        logger.log(Level.WARNING, "MESSS ->" + Parser.parse(message.toXML().toString()));
        WritableMap params = Arguments.createMap();
        MamResultExtension mamResultExtension = MamResultExtension.from(message);
//
//        Forwarded forwarded = mamResultExtension.getForwarded();
//        Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
//        logger.log(Level.WARNING, "FORWARDED---------->" + Parser.parse(forwarded.toXML().toString()));
//
//        Message forwardedMessage = (Message) forwarded.getForwardedStanza();
//
//        logger.log(Level.WARNING, "ELE---------->" + forwardedMessage.getExtensions().toString());
//        WritableMap forwardedMessageMap = Arguments.createMap();
//        forwardedMessageMap.putString("from", forwardedMessage.getFrom().toString());
//        forwardedMessageMap.putString("to", forwardedMessage.getTo().toString());
//        forwardedMessageMap.putString("body", forwardedMessage.getBody().toString());
//        forwardedMessageMap.putString("type", forwardedMessage.getType().toString());
//        try {
//            String obj = forwardedMessage.toXML().toString();
//            forwardedMessageMap.putString("id", getId(obj));
//            logger.log(Level.WARNING, "IDDDDD---------->" + obj);
//        } catch(XmlPullParserException | IOException err) {
//
//        }
//
//        WritableMap forwardedMap = Arguments.createMap();
//        forwardedMap.putMap("message", forwardedMessageMap);
//
//        WritableMap delayMap = Arguments.createMap();
//        delayMap.putString("stamp", forwarded.getDelayInformation().getStamp().toString());
//        forwardedMap.putMap("delay", delayMap);
//
//        WritableMap resultMap = Arguments.createMap();
//        resultMap.putMap("forwarded", forwardedMap);
//
//        params.putMap("result", resultMap);
        sendEvent(reactContext, RNXMPP_MESSAGE, Parser.parse(message.toXML().toString()));
    }

    @Override
    public void onRosterReceived(Roster roster) {
        WritableArray rosterResponse = Arguments.createArray();
        for (RosterEntry rosterEntry : roster.getEntries()) {
            WritableMap rosterProps = Arguments.createMap();
            rosterProps.putString("username", rosterEntry.getUser());
            rosterProps.putString("displayName", rosterEntry.getName());
            WritableArray groupArray = Arguments.createArray();
            for (RosterGroup rosterGroup : rosterEntry.getGroups()) {
                groupArray.pushString(rosterGroup.getName());
            }
            rosterProps.putArray("groups", groupArray);
            rosterProps.putString("subscription", rosterEntry.getType().toString());
            rosterResponse.pushMap(rosterProps);
        }
        sendEvent(reactContext, RNXMPP_ROSTER, rosterResponse);
    }

    @Override
    public void onIQ(IQ iq) {
        Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
        logger.log(Level.WARNING, "iQ ->" + Parser.parse(iq.toXML().toString()));
        sendEvent(reactContext, RNXMPP_IQ, Parser.parse(iq.toXML().toString()));
    }

    @Override
    public void onPresence(Presence presence) {
//        WritableMap presenceMap = Arguments.createMap();
//        presenceMap.putString("type", presence.getType().toString());
//        presenceMap.putString("from", presence.getFrom().toString());
//        presenceMap.putString("status", presence.getStatus());
//        presenceMap.putString("mode", presence.getMode().toString());
        sendEvent(reactContext, RNXMPP_PRESENCE, Parser.parse(presence.toXML().toString()));
    }

    @Override
    public void onConnnect(String username, String password) {
        Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());
        logger.log(Level.WARNING, "on Connect ->" + username);
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_CONNECT, params);
    }

    @Override
    public void onDisconnect(Exception e) {
        sendEvent(reactContext, RNXMPP_DISCONNECT, e.getLocalizedMessage());
    }

    @Override
    public void onLogin(String username, String password) {
        WritableMap params = Arguments.createMap();
        params.putString("username", username);
        params.putString("password", password);
        sendEvent(reactContext, RNXMPP_LOGIN, params);
    }

    void sendEvent(ReactContext reactContext, String eventName, @Nullable Object params) {
        reactContext
                .getJSModule(RCTNativeAppEventEmitter.class)
                .emit(eventName, params);
    }
}
