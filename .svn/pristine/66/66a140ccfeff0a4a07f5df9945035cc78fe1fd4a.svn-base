package org.frontier.teamcity.testRailIntegration;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Utils {
    public static <T> Collection<T> getDuplicates(Collection<T> coll){
        return coll.stream().filter(e->Collections.frequency(coll,e) > 1).collect(Collectors.toList());
    }

    public static <T> boolean hasDuplicates(Collection<T> coll){
        return coll.size() != coll.stream().distinct().count();
    }

    public static <K,V> V tryGet(Map<K,V> map, K key){
        if(map.containsKey(key)){
            return map.get(key);
        }else {
            throw new IllegalArgumentException(String.format("The key %s does not exist in the map", key));
        }
    }
}

