package org.nodel;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

/**
 * Convenience classes / methods to support generic Tuples (pairs, triples, etc.)
 */
public class Tuple {
    
    public static class T1<X1> {
        
        private X1 _item1;
        
        public T1(X1 value1) {
            _item1 = value1;
        }
        
        public X1 getItem1() {
            return _item1;
        }
        
        public String toString() {
            return String.format("{ %s }", _item1);
        }
        
    } // (class)
    
    public static class T2<X1, X2> {
        
        private X1 _item1;
        
        private X2 _item2;
        
        public T2(X1 value1, X2 value2) {
            _item1 = value1;
            _item2 = value2;
        }
        
        public X1 getItem1() {
            return _item1;
        }
        
        public X2 getItem2() {
            return _item2;
        }
        
        public String toString() {
            return String.format("{ %s, %s }", _item1, _item2);
        }
        
    } // (class)
    
    public static class T3<X1, X2, X3> {
        
        private X1 _item1;
        
        private X2 _item2;
        
        private X3 _item3;
        
        public T3(X1 value1, X2 value2, X3 value3) {
            _item1 = value1;
            _item2 = value2;
            _item3 = value3;
        }
        
        public X1 getItem1() {
            return _item1;
        }
        
        public X2 getItem2() {
            return _item2;
        }       
        
        public X3 getItem3() {
            return _item3;
        }        
        
        public String toString() {
            return String.format("{ %s, %s, %s }", _item1, _item2, _item3);
        }        
        
    } // (class)
    
    public static class T4<X1, X2, X3, X4> {
        
        private X1 _item1;
        
        private X2 _item2;
        
        private X3 _item3;
        
        private X4 _item4;
        
        public T4(X1 value1, X2 value2, X3 value3, X4 value4) {
            _item1 = value1;
            _item2 = value2;
            _item3 = value3;
            _item4 = value4;
        }
        
        public X1 getItem1() {
            return _item1;
        }
        
        public X2 getItem2() {
            return _item2;
        }
        
        public X3 getItem3() {
            return _item3;
        }
        
        public X4 getItem4() {
            return _item4;
        }
        
        public String toString() {
            return String.format("{ %s, %s, %s, %s }", _item1, _item2, _item3, _item4);
        }        
        
    } // (class)     
    
} // (class)
