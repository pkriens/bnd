package foo.bar;

class Foo {
    /**
     * A function to turn a  Dictionary to a Map
     */
    public static Map<String, Object> toMap(Dictionary<String, Object> d) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (Enumeration<String> e = d.keys(); e.hasMoreElements();) {
            String key = e.nextElement();
            map.put(key, d.get(key));
        }
        return map;
    }

    /**
     * Use bnd HttpClient to get a URL, convert the result body from JSON to a Map
     * 
     */

     //+ add a simple java class that implements a linked list with next and prev

        
}