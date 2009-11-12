/*
 * Copyright 2006-2009 ConSol* Software GmbH.
 * 
 * This file is part of Citrus.
 * 
 *  Citrus is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Citrus is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Citrus.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.consol.citrus.adapter.handler;

import javax.jms.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.core.Message;
import org.springframework.integration.jms.DefaultJmsHeaderMapper;
import org.springframework.integration.jms.HeaderMappingMessageConverter;
import org.springframework.integration.message.MessageBuilder;
import org.springframework.jms.connection.ConnectionFactoryUtils;
import org.springframework.jms.support.JmsUtils;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DynamicDestinationResolver;
import org.springframework.util.StringUtils;

import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.message.MessageHandler;

public class JmsConnectingMessageHandler implements MessageHandler {

    private Destination destination;
    
    private String destinationName;

    private Destination replyDestination;
    
    private String replyDestinationName;
    
    private ConnectionFactory connectionFactory;
    
    private long replyTimeout = 5000L;
    
    private JmsMessageCallback messageCallback;
    
    /**
     * Logger
     */
    private static final Logger log = LoggerFactory.getLogger(JmsConnectingMessageHandler.class);

    public static interface JmsMessageCallback {
        /** Opportunity to decorate generated jms message before forwarding */
        void doWithMessage(javax.jms.Message message, Message<?> request) throws JMSException;
    }

    /**
     * @see com.consol.citrus.message.MessageHandler#handleMessage(org.springframework.integration.core.Message)
     * @throws CitrusRuntimeException
     */
    public Message<?> handleMessage(final Message<?> request) {
        log.info("Forwarding request to: " + getDestinationName());

        if(log.isDebugEnabled()) {
            log.debug("Message is: " + request.getPayload());
        }

        Connection connection = null;
        Session session = null;
        MessageProducer messageProducer = null;
        MessageConsumer messageConsumer = null;
        Destination replyDestination = null;
        
        Message<?> replyMessage = null;
        try {
            connection = createConnection();
            session = createSession(connection);
            javax.jms.Message jmsRequest = getMessageConverter().toMessage(request, session);
            
            messageProducer = session.createProducer(getDestination(session));

            replyDestination = getReplyDestination(session, request);
            jmsRequest.setJMSReplyTo(replyDestination);
            
            if(messageCallback != null) {
                messageCallback.doWithMessage(jmsRequest, request);
            }
            
            connection.start();
            messageProducer.send(jmsRequest);
            if (replyDestination instanceof TemporaryQueue || replyDestination instanceof TemporaryTopic) {
                messageConsumer = session.createConsumer(replyDestination);
            } else {
                String messageId = jmsRequest.getJMSMessageID().replaceAll("'", "''");
                String messageSelector = "JMSCorrelationID = '" + messageId + "'";
                messageConsumer = session.createConsumer(replyDestination, messageSelector);
            }
            
            javax.jms.Message jmsReplyMessage = (this.replyTimeout >= 0) ? messageConsumer.receive(replyTimeout) : messageConsumer.receive();
            
            if(jmsReplyMessage != null) {
                replyMessage = (Message<?>)getMessageConverter().fromMessage(jmsReplyMessage);
            } else {
                log.info("Did not receive reply message from destination '"
                        + getReplyDestination(session, request)
                        + "' - generating empty response");
                
                replyMessage = MessageBuilder.withPayload("").build();
            }
        } catch (JMSException e) {
            throw new CitrusRuntimeException(e);
        } finally {
            JmsUtils.closeMessageProducer(messageProducer);
            JmsUtils.closeMessageConsumer(messageConsumer);
            deleteTemporaryDestination(replyDestination);
            JmsUtils.closeSession(session);
            
            if(connection != null) {
                ConnectionFactoryUtils.releaseConnection(connection, this.connectionFactory, true);
            }
        }
        
        return replyMessage;
    }
    
    private void deleteTemporaryDestination(Destination destination) {
        try {
            if (destination instanceof TemporaryQueue) { 
                ((TemporaryQueue) destination).delete();
            } else if (destination instanceof TemporaryTopic) {
                ((TemporaryTopic) destination).delete();
            }
        } catch (JMSException e) {
            log.error("Error while deleting temporary destination", e);
        }
    }

    private Destination getReplyDestination(Session session, Message<?> message) throws JMSException {
        if(message.getHeaders().getReplyChannel() != null) {
            if(message.getHeaders().getReplyChannel() instanceof Destination) {
                return (Destination)message.getHeaders().getReplyChannel();
            } else {
                return new DynamicDestinationResolver().resolveDestinationName(session, 
                                message.getHeaders().getReplyChannel().toString(), 
                                false);
            }
        } else if (replyDestination != null) {
            return replyDestination;
        } else if (StringUtils.hasText(replyDestinationName)) {
            return new DynamicDestinationResolver().resolveDestinationName(session, this.replyDestinationName, false);
        }
        
        return session.createTemporaryQueue();
    }

    private Destination getDestination(Session session) throws JMSException {
        if (destination != null) {
            return destination;
        }
        
        return new DynamicDestinationResolver().resolveDestinationName(session, destinationName, false);
    }
    
    /**
     * @return the destinationName
     */
    protected String getDestinationName() {
        try {
            if(destination != null) {
                if(destination instanceof Queue) {
                    return ((Queue)destination).getQueueName();
                } else if(destination instanceof Topic) {
                    return ((Topic)destination).getTopicName();
                } else {
                    return destination.toString();
                }
            } else {
                return destinationName;
            }
        } catch (JMSException e) {
            log.error("Error while getting destination name", e);
            return "";
        }
    }
    
    /**
     * @return
     * @throws JMSException
     */
    protected Connection createConnection() throws JMSException {
        if (connectionFactory instanceof QueueConnectionFactory) {
            return ((QueueConnectionFactory) connectionFactory).createQueueConnection();
        }
        return connectionFactory.createConnection();
    }
    
    /**
     * 
     * @param connection
     * @return
     * @throws JMSException
     */
    protected Session createSession(Connection connection) throws JMSException {
        if (connection instanceof QueueConnection) {
            return ((QueueConnection) connection).createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
        }
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }
    
    /**
     * 
     * @return
     */
    protected MessageConverter getMessageConverter() {
        HeaderMappingMessageConverter hmmc = new HeaderMappingMessageConverter(new DefaultJmsHeaderMapper());
        hmmc.setExtractIntegrationMessagePayload(true);

        return hmmc;
    }

    /**
     * @param connectionFactory the connectionFactory to set
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void setReplyDestination(String replyDestination) {
        this.replyDestinationName = replyDestination;
    }

    public void setSendDestination(String sendDestination) {
        this.destinationName = sendDestination;
    }
    
    /**
     * @param destination the destination to set
     */
    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    /**
     * @param destinationName the destinationName to set
     */
    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    /**
     * @param replyDestination the replyDestination to set
     */
    public void setReplyDestination(Destination replyDestination) {
        this.replyDestination = replyDestination;
    }

    /**
     * @param replyDestinationName the replyDestinationName to set
     */
    public void setReplyDestinationName(String replyDestinationName) {
        this.replyDestinationName = replyDestinationName;
    }

    /**
     * @param replyTimeout the replyTimeout to set
     */
    public void setReplyTimeout(long replyTimeout) {
        this.replyTimeout = replyTimeout;
    }

    /**
     * @param messageCallback the messageCallback to set
     */
    public void setMessageCallback(JmsMessageCallback messageCallback) {
        this.messageCallback = messageCallback;
    }
}
