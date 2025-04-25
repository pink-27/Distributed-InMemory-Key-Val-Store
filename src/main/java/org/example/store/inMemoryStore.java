package org.example.store;

import java.util.concurrent.ConcurrentHashMap;

public class inMemoryStore{
    ConcurrentHashMap<String,String>store;
    public inMemoryStore(){
        store = new ConcurrentHashMap<>();
    }
    public void updateKeyVal(String Key, String Val){
        store.put(Key, Val);
    }
    public String getValue(String Key){
        if(store.containsKey(Key)){
            return store.get(Key);
        }
        return null;
    }

}