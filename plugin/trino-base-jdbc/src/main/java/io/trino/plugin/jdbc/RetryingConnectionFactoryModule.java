/*
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
package io.trino.plugin.jdbc;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.trino.plugin.jdbc.RetryingConnectionFactory.DefaultRetryStrategy;
import io.trino.plugin.jdbc.RetryingConnectionFactory.RetryStrategy;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.trino.plugin.base.inject.DecoratorBinder.newDecoratorBinder;

public class RetryingConnectionFactoryModule
        extends AbstractConfigurationAwareModule
{
    @Override
    public void setup(Binder binder)
    {
        configBinder(binder).bindConfig(QueryConfig.class);

        newDecoratorBinder(binder, ConnectionFactory.class, ForBaseJdbc.class)
                .addBinding(RetryingConnectionFactory.FactoryDecorator.class);

        newOptionalBinder(binder, RetryStrategy.class)
                .setDefault()
                .to(DefaultRetryStrategy.class)
                .in(Scopes.SINGLETON);
    }
}
