# async-message-broker

[![Build Status](https://travis-ci.org/hmrc/async-message-broker.svg)](https://travis-ci.org/hmrc/async-message-broker) [ ![Download](https://api.bintray.com/packages/hmrc/releases/async-message-broker/images/download.svg) ](https://bintray.com/hmrc/releases/async-message-broker/_latestVersion)

The async-message-broker is a chat room POC which interfaces with the CICSO Spark API. The micro-service exposes a number of API's to support the CRUD functions for chat rooms and also exposes a web chat interface. 

The POC will allow a user to create a chat room and invite other users into the chat room using a single page CSS JavaScript application. 

To run the micro-service, the following steps are required. 

1. Create a CISCO Spark account. https://www.ciscospark.com/
2. Update application.conf config keys bot.bearerToken (with the oauth bearer token acquired from the new CICSO spark account) and bot.email, which is the email address associated with the new account. 
3. Start the micro-service on port 2345 and enter the below URL into a browser to start the web chat CSS JavaScript application. Requesting the below URL will auto create the Spark Room and assign the csa (csaEmail) to the chat room. 
http://localhost:2345/async-message-broker/chat?csaEmail=someemailaddress.com&roomName=Chat-Room&pageId=1 
4. Once the chat room has been created, the chat UI will be displayed. Enter a message and press Send. The message will be delivered to the associated Spark room and the UI chat window will be updated with the message. 
5. Using the Spark console with the CSA email account (supplied in step 3), enter messages in the chat room (supplied in step 3) and then watch the web chat UI auto update with the messages from the CSA.


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
