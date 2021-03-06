/*
 * Copyright 2006-2014 the original author or authors.
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

package com.consol.citrus.config.xml;

import com.consol.citrus.actions.*;
import com.consol.citrus.container.SequenceAfterSuite;
import com.consol.citrus.testng.AbstractBeanDefinitionParserTest;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * @author Christoph Deppisch
 * @since 2.0
 */
public class SequenceAfterSuiteParserTest extends AbstractBeanDefinitionParserTest {

    @BeforeClass
    @Override
    protected void parseBeanDefinitions() {
    }

    @Test
    public void testSequenceAfterParser() throws Exception {
        beanDefinitionContext = createApplicationContext("context");
        Map<String, SequenceAfterSuite> container = beanDefinitionContext.getBeansOfType(SequenceAfterSuite.class);

        Assert.assertEquals(container.size(), 2L);

        SequenceAfterSuite sequenceAfter = container.get("afterSuite");
        Assert.assertEquals(sequenceAfter.getName(), "afterSuite");
        Assert.assertEquals(sequenceAfter.getSuiteNames().size(), 0L);
        Assert.assertEquals(sequenceAfter.getTestGroups().size(), 0L);
        Assert.assertEquals(sequenceAfter.getActions().size(), 3L);

        Assert.assertEquals(sequenceAfter.getActions().get(0).getClass(), EchoAction.class);
        Assert.assertEquals(sequenceAfter.getActions().get(1).getClass(), CustomTestAction.class);
        Assert.assertEquals(sequenceAfter.getActions().get(2).getClass(), EchoAction.class);

        Assert.assertTrue(sequenceAfter.shouldExecute("", null));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteA", null));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteB", null));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteZ", new String[] {"unit"}));

        sequenceAfter.execute(context);

        sequenceAfter = container.get("afterSuite2");
        Assert.assertEquals(sequenceAfter.getName(), "afterSuite2");
        Assert.assertEquals(sequenceAfter.getSuiteNames().size(), 2L);
        Assert.assertEquals(sequenceAfter.getTestGroups().size(), 2L);
        Assert.assertEquals(sequenceAfter.getActions().size(), 1L);

        Assert.assertFalse(sequenceAfter.shouldExecute("", null));
        Assert.assertFalse(sequenceAfter.shouldExecute("suiteA", null));
        Assert.assertFalse(sequenceAfter.shouldExecute("suiteB", null));
        Assert.assertFalse(sequenceAfter.shouldExecute("suiteZ", null));
        Assert.assertFalse(sequenceAfter.shouldExecute("suiteZ", new String[]{}));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteZ", new String[]{"unit"}));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteZ", new String[]{"e2e"}));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteA", new String[]{"unit"}));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteB", new String[]{"other", "unit", "e2e"}));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteA", new String[] {"e2e"}));
        Assert.assertTrue(sequenceAfter.shouldExecute("suiteB", new String[] {"other", "unit", "e2e"}));

        Assert.assertEquals(sequenceAfter.getActions().get(0).getClass(), SleepAction.class);

        sequenceAfter.execute(context);
    }
}
