package rnxmpp.service;

import com.facebook.react.bridge.ReadableArray;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.mam.MamManager.MamQueryResult;

import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.impl.JidCreate;

import android.os.AsyncTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.HostnameVerifier;
import rnxmpp.ssl.UnsafeSSLContext;


/**
 * Created by Kristian Frølund on 7/19/16.
 * Copyright (c) 2016. Teletronics. All rights reserved
 */

public class XmppServiceSmackImpl implements XmppService, ChatManagerListener, StanzaListener, ConnectionListener, ChatMessageListener, RosterLoadedListener {
    XmppServiceListener xmppServiceListener;
    Logger logger = Logger.getLogger(XmppServiceSmackImpl.class.getName());

    XMPPTCPConnection connection;
    Roster roster;
    List<String> trustedHosts = new ArrayList<>();
    String password;

    public XmppServiceSmackImpl(XmppServiceListener xmppServiceListener) {
        this.xmppServiceListener = xmppServiceListener;
    }

    @Override
    public void trustHosts(ReadableArray trustedHosts) {
        for(int i = 0; i < trustedHosts.size(); i++){
            this.trustedHosts.add(trustedHosts.getString(i));
        }
    }

    @Override
    public void connect(String jid, String password, String authMethod, String hostname, Integer port) {
        final String[] jidParts = jid.split("@");
        String[] serviceNameParts = jidParts[1].split("/");
        String domain = serviceNameParts[0];
        
        try {
        DomainBareJid serviceName = JidCreate.domainBareFrom(domain);
        InetAddress addr = InetAddress.getByName(hostname);
            HostnameVerifier verifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return false;
                }
            };
       XMPPTCPConnectionConfiguration.Builder confBuilder = XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain(serviceName)
                .setUsernameAndPassword(jidParts[0], password)
                .setConnectTimeout(3000)
                .setHostnameVerifier(verifier)
                .setDebuggerEnabled(true)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);

        if (serviceNameParts.length>1){
            confBuilder.setResource(serviceNameParts[1]);
        } else {
            confBuilder.setResource(Long.toHexString(Double.doubleToLongBits(Math.random())));
        }
        if (hostname != null){
            // confBuilder.setHost(hostname);
            confBuilder.setHostAddress(addr);
        }
        if (port != null){
            confBuilder.setPort(port);
        }
        if (trustedHosts.contains(hostname) || (hostname == null && trustedHosts.contains(serviceName))){
            confBuilder.setCustomSSLContext(UnsafeSSLContext.INSTANCE.getContext());
        }
        XMPPTCPConnectionConfiguration connectionConfiguration = confBuilder.build();
        connection = new XMPPTCPConnection(connectionConfiguration);
        connection.setUseStreamManagement(true);
        connection.setUseStreamManagementResumptionDefault(true);
        connection.addSyncStanzaListener(this, new OrFilter(new OrFilter(new StanzaTypeFilter(IQ.class), new StanzaTypeFilter(Presence.class) ), new StanzaTypeFilter(Message.class) ));
//        connection.addAsyncStanzaListener(this, new StanzaTypeFilter(Message.class));

        connection.addConnectionListener(this);

        ChatManager.getInstanceFor(connection).addChatListener(this);
        roster = Roster.getInstanceFor(connection);
        roster.addRosterLoadedListener(this);

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    connection.connect().login();
                } catch (XMPPException | SmackException | InterruptedException | IOException e) {
                    logger.log(Level.SEVERE, "Could not login for user " + jidParts[0], e);
                    if (e instanceof SASLErrorException){
                        XmppServiceSmackImpl.this.xmppServiceListener.onLoginError(((SASLErrorException) e).getSASLFailure().toString());
                    }else{
                        XmppServiceSmackImpl.this.xmppServiceListener.onError(e);
                    }

                }
                return null;
            }

            @Override
            protected void onPostExecute(Void dummy) {

            }
        }.execute();
        } catch (XmppStringprepException | UnknownHostException err) {
              logger.log(Level.WARNING, "Could not get domainbarejid", err);
        };
        
 
    }

    @Override
    public void message(String text, String to, String thread) {

        try {
            String chatIdentifier = (thread == null ? to : thread);
            EntityJid peerJid = JidCreate.entityBareFrom(to);
            ChatManager chatManager = ChatManager.getInstanceFor(connection);
            Chat chat = chatManager.getThreadChat(chatIdentifier);
            if (chat == null) {
                if (thread == null){
                    chat = chatManager.createChat(peerJid, this);
                }else{
                    chat = chatManager.createChat(peerJid, thread, this);
                }
            }
            chat.sendMessage(text);
        } catch (SmackException | XmppStringprepException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send message", e);
        }
    }

    @Override
    public void presence(String to, String type) {
        try {
            Jid peerJid = JidCreate.from(to);
            connection.sendStanza(new Presence(peerJid, Presence.Type.fromString(type)));
        } catch (SmackException.NotConnectedException | InterruptedException | XmppStringprepException e) {
            logger.log(Level.WARNING, "Could not send presence", e);
        }
    }

    @Override
    public void removeRoster(String to) {
        try {
        Roster roster = Roster.getInstanceFor(connection);
        RosterEntry rosterEntry = roster.getEntry(JidCreate.entityBareFrom(to));
        if (rosterEntry != null){
            try {
                roster.removeEntry(rosterEntry);
            } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | XMPPException.XMPPErrorException | SmackException.NoResponseException | InterruptedException e) {
                logger.log(Level.WARNING, "Could not remove roster entry: " + to);
            }
        }
        } catch(XmppStringprepException e) {
            logger.log(Level.WARNING, "Could not send presence", e);
        }
    }

    @Override
    public void disconnect() {
        connection.disconnect();
    }

    @Override
    public void fetchRoster() {
        try {
            roster.reload();
        } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not fetch roster", e);
        }
    }

    public class StanzaPacket extends org.jivesoftware.smack.packet.Stanza {
         private String xmlString;

         public StanzaPacket(String xmlString) {
             super();
             this.xmlString = xmlString;
         }

         @Override
         public XmlStringBuilder toXML() {
             XmlStringBuilder xml = new XmlStringBuilder();
             xml.append(this.xmlString);
             return xml;
         }

         @Override
         public String toString() {
              logger.log(Level.INFO, "Stanza Packet To String");
             return xmlString;
         }
    }

    @Override
    public void sendStanza(String stanza) {
        StanzaPacket packet = new StanzaPacket(stanza);
        try {
            connection.sendStanza(packet);
        } catch (SmackException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send stanza", e);
        }
    }

    @Override
    public void querryLastArchivedMessages(Integer limit, String jid, String before) {
        
        try {
            MamManager mamManager = MamManager.getInstanceFor(connection);
            Jid peerJid = JidCreate.from(jid);
            MamQueryResult mamQueryResult = mamManager.pageBefore(peerJid, before, limit);
        } catch (SmackException | XmppStringprepException | XMPPException.XMPPErrorException | InterruptedException e) {
            logger.log(Level.WARNING, "Could not send stanza", e);
        }
    }

    @Override
    public void chatCreated(Chat chat, boolean createdLocally) {
        chat.addMessageListener(this);
    }

    @Override
    public void processStanza(Stanza packet) throws SmackException.NotConnectedException {
        if (packet instanceof IQ){
            this.xmppServiceListener.onIQ((IQ) packet);
        }else if (packet instanceof Presence){
            this.xmppServiceListener.onPresence((Presence) packet);
        }else if(packet instanceof Message) {
            this.xmppServiceListener.onMessage((Message) packet);
        }else{
            logger.log(Level.WARNING, "Got a Stanza, of unknown subclass", packet.toXML());
        }
    }

    @Override
    public void processMessage(Chat chat, Message message) {

    }

    @Override
    public void connected(XMPPConnection connection) {
        try {
            logger.log(Level.WARNING, "on Connected -----> ", password);
            if (!(connection.getUser()==null)) {
                this.xmppServiceListener.onConnnect(connection.getUser().toString(), password);
            }

        } catch(NullPointerException err) {
            logger.log(Level.WARNING, "on Connected -----> ", err);
        }
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        EntityFullJid fullJid = connection.getUser();
        this.xmppServiceListener.onLogin(fullJid.toString(), password);
        this.xmppServiceListener.onConnnect(connection.getUser().toString(), password);
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        this.xmppServiceListener.onRosterReceived(roster);
    }
    @Override
    public void onRosterLoadingFailed(Exception e){
        logger.log(Level.WARNING, "Could loading roster", e);
    }
    @Override
    public void connectionClosedOnError(Exception e) {
        this.xmppServiceListener.onDisconnect(e);
    }

    @Override
    public void connectionClosed() {
        logger.log(Level.INFO, "Connection was closed.");
    }

    @Override
    public void reconnectionSuccessful() {
        logger.log(Level.INFO, "Did reconnect");
    }

    @Override
    public void reconnectingIn(int seconds) {
        logger.log(Level.INFO, "Reconnecting in {0} seconds", seconds);
    }

    @Override
    public void reconnectionFailed(Exception e) {
        logger.log(Level.WARNING, "Could not reconnect", e);

    }

}
