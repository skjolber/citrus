/*
 * Copyright 2006-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.dsl.definition;

import com.consol.citrus.actions.SendMessageAction;
import com.consol.citrus.container.SequenceAfterTest;
import com.consol.citrus.container.SequenceBeforeTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.endpoint.Endpoint;
import com.consol.citrus.message.*;
import com.consol.citrus.report.TestActionListeners;
import com.consol.citrus.testng.AbstractTestNGUnitTest;
import com.consol.citrus.validation.builder.*;
import com.consol.citrus.validation.interceptor.XpathMessageConstructionInterceptor;
import com.consol.citrus.variable.MessageHeaderVariableExtractor;
import com.consol.citrus.variable.XpathPayloadVariableExtractor;
import org.easymock.EasyMock;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.xstream.XStreamMarshaller;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;

import static org.easymock.EasyMock.*;

/**
 * @author Christoph Deppisch
 */
public class SendMessageDefinitionTest extends AbstractTestNGUnitTest {
    
    private Endpoint messageEndpoint = EasyMock.createMock(Endpoint.class);

    private ApplicationContext applicationContextMock = EasyMock.createMock(ApplicationContext.class);
    private Resource resource = EasyMock.createMock(Resource.class);

    private XStreamMarshaller marshaller = new XStreamMarshaller();

    @BeforeClass
    public void prepareMarshaller() {
        marshaller.getXStream().processAnnotations(TestRequest.class);
    }

    @Test
    public void testSendBuilderWithMessageInstance() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .message(new DefaultMessage("Foo").setHeader("operation", "foo"));
            }
        };
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);
        
        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "Foo");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 1L);
        Assert.assertEquals(messageBuilder.getMessageHeaders().get("operation"), "foo");
    }

    @Test
    public void testSendBuilderWithObjectMessageInstance() {
        final Message message = new DefaultMessage(new Integer(10)).setHeader("operation", "foo");
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .message(message);
            }
        };

        builder.execute();

        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);

        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), StaticMessageContentBuilder.class);

        StaticMessageContentBuilder messageBuilder = (StaticMessageContentBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getMessage().getPayload(), 10);
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        Assert.assertEquals(messageBuilder.getMessage().copyHeaders().size(), message.copyHeaders().size());
        Assert.assertEquals(messageBuilder.getMessage().getHeader(MessageHeaders.ID), message.getHeader(MessageHeaders.ID));
        Assert.assertEquals(messageBuilder.getMessage().getHeader("operation"), "foo");

        Message constructed = messageBuilder.buildMessageContent(new TestContext(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(constructed.copyHeaders().size(), message.copyHeaders().size());
        Assert.assertEquals(constructed.getHeader("operation"), "foo");
        Assert.assertEquals(constructed.getHeader(MessageHeaders.ID), message.getHeader(MessageHeaders.ID));
    }

    @Test
    public void testSendBuilderWithObjectMessageInstanceAdditionalHeader() {
        final Message message = new DefaultMessage(new Integer(10)).setHeader("operation", "foo");
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .message(message)
                    .header("additional", "new");
            }
        };

        builder.execute();

        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);

        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), StaticMessageContentBuilder.class);

        StaticMessageContentBuilder messageBuilder = (StaticMessageContentBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getMessage().getPayload(), 10);
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 1L);
        Assert.assertEquals(messageBuilder.getMessageHeaders().get("additional"), "new");
        Assert.assertEquals(messageBuilder.getMessage().copyHeaders().size(), message.copyHeaders().size());
        Assert.assertEquals(messageBuilder.getMessage().getHeader(MessageHeaders.ID), message.getHeader(MessageHeaders.ID));
        Assert.assertEquals(messageBuilder.getMessage().getHeader("operation"), "foo");

        Message constructed = messageBuilder.buildMessageContent(new TestContext(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(constructed.copyHeaders().size(), message.copyHeaders().size() + 1);
        Assert.assertEquals(constructed.getHeader("operation"), "foo");
        Assert.assertEquals(constructed.getHeader("additional"), "new");
    }

    @Test
    public void testSendBuilderWithPayloadModel() {
        reset(applicationContextMock);
        expect(applicationContextMock.getBean(TestActionListeners.class)).andReturn(new TestActionListeners()).once();
        expect(applicationContextMock.getBeansOfType(SequenceBeforeTest.class)).andReturn(new HashMap<String, SequenceBeforeTest>()).once();
        expect(applicationContextMock.getBeansOfType(SequenceAfterTest.class)).andReturn(new HashMap<String, SequenceAfterTest>()).once();
        expect(applicationContextMock.getBean(Marshaller.class)).andReturn(marshaller).once();
        replay(applicationContextMock);

        MockBuilder builder = new MockBuilder(applicationContextMock) {
            @Override
            public void configure() {
                send(messageEndpoint)
                        .payloadModel(new TestRequest("Hello Citrus!"));
            }
        };

        builder.execute();

        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);

        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);

        verify(applicationContextMock);
    }

    @Test
    public void testSendBuilderWithPayloadModelExplicitMarshaller() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload(new TestRequest("Hello Citrus!"), marshaller);
            }
        };

        builder.execute();

        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);

        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);

        verify(applicationContextMock);
    }

    @Test
    public void testSendBuilderWithPayloadModelExplicitMarshallerName() {
        reset(applicationContextMock);
        expect(applicationContextMock.getBean(TestActionListeners.class)).andReturn(new TestActionListeners()).once();
        expect(applicationContextMock.getBeansOfType(SequenceBeforeTest.class)).andReturn(new HashMap<String, SequenceBeforeTest>()).once();
        expect(applicationContextMock.getBeansOfType(SequenceAfterTest.class)).andReturn(new HashMap<String, SequenceAfterTest>()).once();
        expect(applicationContextMock.getBean("myMarshaller", Marshaller.class)).andReturn(marshaller).once();
        replay(applicationContextMock);

        MockBuilder builder = new MockBuilder(applicationContextMock) {
            @Override
            public void configure() {
                send(messageEndpoint)
                        .payload(new TestRequest("Hello Citrus!"), "myMarshaller");
            }
        };

        builder.execute();

        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);

        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);

        verify(applicationContextMock);
    }
    
    @Test
    public void testSendBuilderWithPayloadData() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload("<TestRequest><Message>Hello World!</Message></TestRequest>");
            }
        };
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
    }

    @Test
    public void testSendBuilderWithPayloadResource() throws IOException {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload(resource);
            }
        };
        
        reset(resource);
        expect(resource.getInputStream()).andReturn(new ByteArrayInputStream("somePayloadData".getBytes())).once();
        replay(resource);
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);
        
        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "somePayloadData");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        
        verify(resource);
    }
    
    @Test
    public void testSendBuilderWithEndpointName() {
        reset(applicationContextMock);
        expect(applicationContextMock.getBean(TestActionListeners.class)).andReturn(new TestActionListeners()).once();
        expect(applicationContextMock.getBeansOfType(SequenceBeforeTest.class)).andReturn(new HashMap<String, SequenceBeforeTest>()).once();
        expect(applicationContextMock.getBeansOfType(SequenceAfterTest.class)).andReturn(new HashMap<String, SequenceAfterTest>()).once();
        replay(applicationContextMock);

        MockBuilder builder = new MockBuilder(applicationContextMock) {
            @Override
            public void configure() {
                send("fooMessageEndpoint")
                    .payload("<TestRequest><Message>Hello World!</Message></TestRequest>");
            }
        };

        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        Assert.assertEquals(action.getEndpointUri(), "fooMessageEndpoint");
        
        verify(applicationContextMock);
    }
    
    @Test
    public void testSendBuilderWithHeaders() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                    .header("operation", "foo")
                    .header("language", "eng");
            }
        };
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 2L);
        Assert.assertEquals(messageBuilder.getMessageHeaders().get("operation"), "foo");
        Assert.assertEquals(messageBuilder.getMessageHeaders().get("language"), "eng");
    }
    
    @Test
    public void testSendBuilderWithHeaderData() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                    .header("<Header><Name>operation</Name><Value>foo</Value></Header>");
                
                send(messageEndpoint)
                    .message(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>"))
                    .header("<Header><Name>operation</Name><Value>foo</Value></Header>");
            }
        };
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 2);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        Assert.assertEquals(builder.testCase().getActions().get(1).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        Assert.assertEquals(messageBuilder.getHeaderData().size(), 1L);
        Assert.assertEquals(messageBuilder.getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo</Value></Header>");
        Assert.assertEquals(messageBuilder.getHeaderResources().size(), 0L);

        action = ((SendMessageAction)builder.testCase().getActions().get(1));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        Assert.assertEquals(messageBuilder.getHeaderData().size(), 1L);
        Assert.assertEquals(messageBuilder.getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo</Value></Header>");
        Assert.assertEquals(messageBuilder.getHeaderResources().size(), 0L);
    }

    @Test
    public void testSendBuilderWithMultipleHeaderData() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .header("<Header><Name>operation</Name><Value>foo1</Value></Header>")
                        .header("<Header><Name>operation</Name><Value>foo2</Value></Header>");

                send(messageEndpoint)
                        .message(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>"))
                        .header("<Header><Name>operation</Name><Value>foo1</Value></Header>")
                        .header("<Header><Name>operation</Name><Value>foo2</Value></Header>");
            }
        };

        builder.execute();

        Assert.assertEquals(builder.testCase().getActions().size(), 2);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        Assert.assertEquals(builder.testCase().getActions().get(1).getClass(), SendMessageAction.class);

        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        Assert.assertEquals(messageBuilder.getHeaderData().size(), 2L);
        Assert.assertEquals(messageBuilder.getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo1</Value></Header>");
        Assert.assertEquals(messageBuilder.getHeaderData().get(1), "<Header><Name>operation</Name><Value>foo2</Value></Header>");
        Assert.assertEquals(messageBuilder.getHeaderResources().size(), 0L);

        action = ((SendMessageAction)builder.testCase().getActions().get(1));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        Assert.assertEquals(messageBuilder.getHeaderData().size(), 2L);
        Assert.assertEquals(messageBuilder.getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo1</Value></Header>");
        Assert.assertEquals(messageBuilder.getHeaderData().get(1), "<Header><Name>operation</Name><Value>foo2</Value></Header>");
        Assert.assertEquals(messageBuilder.getHeaderResources().size(), 0L);
    }
    
    @Test
    public void testSendBuilderWithHeaderDataResource() throws IOException {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                    .header(resource);
                
                send(messageEndpoint)
                    .message(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>"))
                    .header(resource);
            }
        };
        
        reset(resource);
        expect(resource.getInputStream()).andReturn(new ByteArrayInputStream("someHeaderData".getBytes())).once();
        expect(resource.getInputStream()).andReturn(new ByteArrayInputStream("otherHeaderData".getBytes())).once();
        replay(resource);
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 2);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        Assert.assertEquals(builder.testCase().getActions().get(1).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        PayloadTemplateMessageBuilder messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        Assert.assertEquals(messageBuilder.getHeaderData().size(), 1L);
        Assert.assertEquals(messageBuilder.getHeaderData().get(0), "someHeaderData");
        Assert.assertEquals(messageBuilder.getHeaderResources().size(), 0L);

        action = ((SendMessageAction)builder.testCase().getActions().get(1));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageBuilder().getClass(), PayloadTemplateMessageBuilder.class);

        messageBuilder = (PayloadTemplateMessageBuilder) action.getMessageBuilder();
        Assert.assertEquals(messageBuilder.getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(messageBuilder.getMessageHeaders().size(), 0L);
        Assert.assertEquals(messageBuilder.getHeaderData().size(), 1L);
        Assert.assertEquals(messageBuilder.getHeaderData().get(0), "otherHeaderData");
        Assert.assertEquals(messageBuilder.getHeaderResources().size(), 0L);
    }
    
    @Test
    public void testReceiveBuilderExtractFromPayload() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                    .extractFromPayload("/TestRequest/Message", "text")
                    .extractFromPayload("/TestRequest/Message/@lang", "language");
            }
        };
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        
        Assert.assertEquals(action.getVariableExtractors().size(), 1);
        Assert.assertTrue(action.getVariableExtractors().get(0) instanceof XpathPayloadVariableExtractor);
        Assert.assertTrue(((XpathPayloadVariableExtractor)action.getVariableExtractors().get(0)).getxPathExpressions().containsKey("/TestRequest/Message"));
        Assert.assertTrue(((XpathPayloadVariableExtractor)action.getVariableExtractors().get(0)).getxPathExpressions().containsKey("/TestRequest/Message/@lang"));
    }
    
    @Test
    public void testReceiveBuilderExtractFromHeader() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                    .payload("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                    .extractFromHeader("operation", "ops")
                    .extractFromHeader("requestId", "id");
            }
        };
        
        builder.execute();
        
        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);
        
        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");
        
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        
        Assert.assertEquals(action.getVariableExtractors().size(), 1);
        Assert.assertTrue(action.getVariableExtractors().get(0) instanceof MessageHeaderVariableExtractor);
        Assert.assertTrue(((MessageHeaderVariableExtractor)action.getVariableExtractors().get(0)).getHeaderMappings().containsKey("operation"));
        Assert.assertTrue(((MessageHeaderVariableExtractor)action.getVariableExtractors().get(0)).getHeaderMappings().containsKey("requestId"));
    }

    @Test
    public void testXpathSupport() {
        MockBuilder builder = new MockBuilder(applicationContext) {
            @Override
            public void configure() {
                send(messageEndpoint)
                        .payload("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                        .xpath("/TestRequest/Message", "Hello World!");
            }
        };

        builder.execute();

        Assert.assertEquals(builder.testCase().getActions().size(), 1);
        Assert.assertEquals(builder.testCase().getActions().get(0).getClass(), SendMessageAction.class);

        SendMessageAction action = ((SendMessageAction)builder.testCase().getActions().get(0));
        Assert.assertEquals(action.getName(), "send");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);

        Assert.assertTrue(action.getMessageBuilder() instanceof AbstractMessageContentBuilder);
        Assert.assertEquals(((AbstractMessageContentBuilder) action.getMessageBuilder()).getMessageInterceptors().size(), 1);
        Assert.assertTrue(((AbstractMessageContentBuilder) action.getMessageBuilder()).getMessageInterceptors().get(0) instanceof XpathMessageConstructionInterceptor);
        Assert.assertEquals(((XpathMessageConstructionInterceptor)((AbstractMessageContentBuilder) action.getMessageBuilder()).getMessageInterceptors().get(0)).getXPathExpressions().get("/TestRequest/Message"), "Hello World!");
    }

}
