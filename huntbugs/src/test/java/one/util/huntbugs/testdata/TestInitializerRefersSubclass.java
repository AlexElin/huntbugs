/*
 * Copyright 2016 HuntBugs contributors
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
package one.util.huntbugs.testdata;

import one.util.huntbugs.registry.anno.AssertNoWarning;
import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author lan
 *
 */
@AssertWarning("InitializerRefersSubclass")
public class TestInitializerRefersSubclass {
    static final TestInitializerRefersSubclass instance = new SubClass();
    
    public static class SubClass extends TestInitializerRefersSubclass {
        
    }
    
    @AssertNoWarning("InitializerRefersSubclass")
    static class AnonymousOk {
        static final AnonymousOk instance = new AnonymousOk() {};
    }
    
    @AssertWarning("InitializerRefersSubclass")
    static class Anonymous {
        static final AnonymousSubClass instance = new AnonymousSubClass() {};
    }
    
    static class AnonymousSubClass extends Anonymous {
        
    }
}
