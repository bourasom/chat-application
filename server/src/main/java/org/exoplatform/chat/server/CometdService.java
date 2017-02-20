package org.exoplatform.chat.server;

import org.cometd.annotation.Listener;
import org.cometd.annotation.Service;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.exoplatform.chat.listener.GuiceManager;
import org.exoplatform.chat.model.MessageBean;
import org.exoplatform.chat.model.RealTimeMessageBean;
import org.exoplatform.chat.services.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.exoplatform.chat.services.UserService;

import java.util.Date;
import java.util.Map;

import static org.exoplatform.chat.services.CometdMessageServiceImpl.COMETD_CHANNEL_NAME;

/**
 * This service is used to receive all Cometd messages and then publish the messages to the right clients.
 * A Cometd service channel is used so the messages sent by clients on this channel are only sent to the server (so
 * by this service) and not to all the subscribed clients. It is up to the server to forward the messages to
 * the right clients.
 */
@Service
public class CometdService {

  UserService userService;
  NotificationService notificationService;
  RealTimeMessageService realTimeMessageService;

  public CometdService() {
    userService = GuiceManager.getInstance().getInstance(UserService.class);
    notificationService = GuiceManager.getInstance().getInstance(NotificationService.class);
    realTimeMessageService = GuiceManager.getInstance().getInstance(RealTimeMessageService.class);
  }

  @Listener(COMETD_CHANNEL_NAME)
  public void onMessageReceived(final ServerSession remoteSession, final ServerMessage message) {
    System.out.println(">>>>>>>>> message received on " + COMETD_CHANNEL_NAME + " : " + message.getJSON());

    //TODO need to verify authorization of the sender.

    try {
      JSONParser jsonParser = new JSONParser();
      JSONObject jsonMessage = (JSONObject) jsonParser.parse((String) message.getData());

      RealTimeMessageBean.EventType eventType = RealTimeMessageBean.EventType.get((String) jsonMessage.get("event"));

      //TODO read each message data and send it each room member. It requires to use 'deliver' instead of 'publish'
      //to avoid broadcasting the message to all connected clients (even the ones not members of the target room)

      ChatService chatService = GuiceManager.getInstance().getInstance(ChatService.class);

      if (eventType.equals(RealTimeMessageBean.EventType.USER_STATUS_CHANGED)) {
        // forward the status change to all connected users
        RealTimeMessageBean realTimeMessageBean = new RealTimeMessageBean(
                RealTimeMessageBean.EventType.USER_STATUS_CHANGED,
                (String) jsonMessage.get("room"),
                (String) jsonMessage.get("sender"),
                new Date(),
                (Map) jsonMessage.get("data"));
        realTimeMessageService.sendMessageToAll(realTimeMessageBean);

        // update data
        userService.setStatus((String) jsonMessage.get("room"),
                (String) ((JSONObject) jsonMessage.get("data")).get("status"),
                (String) jsonMessage.get("dbName"));
      } else if (eventType.equals(RealTimeMessageBean.EventType.MESSAGE_READ)) {
        String room = (String) jsonMessage.get("room");
        String sender = (String) jsonMessage.get("sender");
        String dbName = (String) jsonMessage.get("dbName");

        notificationService.setNotificationsAsRead(sender, "chat", "room", room, dbName);
        if (userService.isAdmin(sender, dbName))
        {
          notificationService.setNotificationsAsRead(UserService.SUPPORT_USER, "chat", "room", room, dbName);
        }

        // send real time message to all others clients of the same user
        RealTimeMessageBean realTimeMessageBean = new RealTimeMessageBean(RealTimeMessageBean.EventType.MESSAGE_READ, room, sender, new Date(), null);
        realTimeMessageService.sendMessage(realTimeMessageBean, sender);
      } else if (eventType.equals(RealTimeMessageBean.EventType.MESSAGE_SENT)) {
        // TODO store message in db
        String room = (String) jsonMessage.get("room");
        String isSystem = jsonMessage.get("isSystem").toString();
        String dbName = (String) jsonMessage.get("dbName");
        String options = jsonMessage.get("options").toString();
        String sender = (String) jsonMessage.get("sender");
        String msg = (String) ((JSONObject)jsonMessage.get("data")).get("msg");
        String targetUser = (String) jsonMessage.get("targetUser");

        chatService.write(msg, sender, room, isSystem, options, dbName, targetUser);
      } else if (eventType.equals(RealTimeMessageBean.EventType.MESSAGE_UPDATED)) {
        String room = jsonMessage.get("room").toString();
        String messageId = ((JSONObject)jsonMessage.get("data")).get("msgId").toString();
        String sender = jsonMessage.get("sender").toString();
        String dbName = jsonMessage.get("dbName").toString();
        // Only author of the message can edit it
        MessageBean currentMessage = chatService.getMessage(room, messageId, dbName);
        if (currentMessage == null || !currentMessage.getUser().equals(sender)) {
          return;
        }

        String msg = ((JSONObject)jsonMessage.get("data")).get("msg").toString();
        chatService.edit(room, sender, messageId, msg, dbName);
      } else if (eventType.equals(RealTimeMessageBean.EventType.MESSAGE_DELETED)) {
        String room = jsonMessage.get("room").toString();
        String messageId = ((JSONObject)jsonMessage.get("data")).get("msgId").toString();
        String sender = jsonMessage.get("sender").toString();
        String dbName = jsonMessage.get("dbName").toString();

        // Only author of the message can delete it
        MessageBean currentMessage = chatService.getMessage(room, messageId, dbName);
        if (currentMessage == null || !currentMessage.getUser().equals(sender)) {
          return;
        }

        chatService.delete(room, sender, messageId, dbName);
      } else if (eventType.equals(RealTimeMessageBean.EventType.FAVOTITE_ADDED)) {
        String sender = jsonMessage.get("sender").toString();
        String targetUser = jsonMessage.get("targetUser").toString();
        String dbName = jsonMessage.get("dbName").toString();
        userService.addFavorite(sender, targetUser, dbName);
      } else if (eventType.equals(RealTimeMessageBean.EventType.FAVORITE_REMOVED)) {
        String sender = jsonMessage.get("sender").toString();
        String targetUser = jsonMessage.get("targetUser").toString();
        String dbName = jsonMessage.get("dbName").toString();
        userService.removeFavorite(sender, targetUser, dbName);
      } else if (eventType.equals(RealTimeMessageBean.EventType.ROOM_DELETED)) {
        String room = jsonMessage.get("room").toString();
        String sender = jsonMessage.get("sender").toString();
        String dbName = jsonMessage.get("dbName").toString();

        chatService.deleteTeamRoom(room, sender, dbName);
      }
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

}