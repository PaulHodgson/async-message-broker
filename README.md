# async-message-broker

[![Build Status](https://travis-ci.org/hmrc/async-message-broker.svg)](https://travis-ci.org/hmrc/async-message-broker) [ ![Download](https://api.bintray.com/packages/hmrc/releases/async-message-broker/images/download.svg) ](https://bintray.com/hmrc/releases/async-message-broker/_latestVersion)

The async-message-broker is a chat room POC which interfaces with the CICSO Spark API. The micro-service exposes a number of API's to support the CRUD functions for chat rooms and also exposes a web chat interface. 

The POC will allow a user to create a chat room and invite other users into the chat room using a single page CSS JavaScript application. 

To run the micro-service, the following steps are required. 

1. Create a CISCO Spark account. https://www.ciscospark.com/
2. Update application.conf config keys bot.bearerToken (with the oauth bearer token acquired from the new CICSO spark account) and bot.email, which is the email address associated with the new account. 
3. Start the micro-service on port 2345 and enter the below URL into a browser to start the web chat CSS JavaScript application.  
http://localhost:2345/async-message-broker/chat 
4. Enter a number value (i.e. 12345) for the clientId and the name of the chat room. Press Create Room. 
5. Once the chat room has been created, the web chat UI will be displayed. Enter a message and press Send. The message will be delivered to the associated Spark room and the UI chat window will be updated with the message. 
6. To invite another user to the Spark chat room, enter the email address of another CISCO spark user into the CSA email input field and press Invite. Using the CISCO Spark console, enter messages into the associated room and then watch the web UI automatically update with the new messages collected from the room. 


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
