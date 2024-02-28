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
package io.trino.likematcher;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

sealed interface Pattern
        permits Pattern.Any, Pattern.Literal, Pattern.ZeroOrMore
{
    record Literal(String value)
            implements Pattern
    {
        @Override
        public String toString()
        {
            return value;
        }
    }

    record ZeroOrMore()
            implements Pattern
    {
        @Override
        public String toString()
        {
            return "%";
        }
    }

    record Any(int length)
            implements Pattern
    {
        public Any
        {
            checkArgument(length > 0, "Length must be > 0");
        }

        @Override
        public String toString()
        {
            return Strings.repeat("_", length);
        }
    }
}
