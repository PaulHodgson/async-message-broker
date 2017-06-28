package com.ciscospark;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;

public class SparkClient {

    private static String sparkURL = "https://api.ciscospark.com/v1";

    static Spark createClient(String accessToken) {
        Spark spark = Spark.builder()
                .baseUrl(URI.create(sparkURL))
                .accessToken(accessToken)
                .build();
        return(spark);
    }

// TODO...PLUG INTO NEW SERVICE!!

    public static Message getMessageFromId(String accessToken, String messageId, Spark spark) {
        if (spark==null) {
            spark=createClient(accessToken);
        }
        Message mess=null;
        try {
            mess = spark.messages().url(new URL(sparkURL+"/messages/"+messageId)).get();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return(mess);
    }

    // TODO...
    public static List<Message> getMessageRooms(String accessToken, String roomId) {
        List<Message> messages = new ArrayList<Message>();

        Spark spark=createClient(accessToken);

        // For each item obtain the message from the message Id!
        spark.messages().queryParam("roomId", roomId).iterate().forEachRemaining(message -> {
//            try {
//                Message mess = spark.messages().url(new URL(sparkURL+"/messages/"+message.getId())).get();
//                messages.add(mess);
//            } catch (MalformedURLException e) {
//                e.printStackTrace();

            messages.add(getMessageFromId(accessToken, message.getId().toString(), spark));
        });
        return(messages);
    }

    public static Message sendMessage(String accessToken, String text, String roomId) {
        Spark spark=createClient(accessToken);

        Message message = new Message();
        message.setRoomId(roomId);
        message.setText(text);
        Message resp = spark.messages().post(message);
        return(resp);
    }

    public static String createRoom(String accessToken, String roomName) {
        Spark spark=createClient(accessToken);

        Room room = new Room();
        room.setTitle(roomName);
        room = spark.rooms().post(room);
        return(room.getId());
    }

    public static Membership createMembership(String accessToken, String roomId, String email) {
        Spark spark=createClient(accessToken);

        Membership membership = new Membership();
        membership.setRoomId(roomId);
        membership.setPersonEmail(email);
        spark.memberships().post(membership);
        return(membership);
    }

}