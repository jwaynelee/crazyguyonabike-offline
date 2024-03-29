/*
 *  JOrtho
 *
 *  Copyright (C) 2005-2009 by i-net software
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as 
 *  published by the Free Software Foundation; either version 2 of the
 *  License, or (at your option) any later version. 
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 *  USA.
 *  
 *  Created on 15.06.2007
 */
package com.cgoab.spelling;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;


/** 
 * With the DictionaryFactory you can create / load a Dictionary. A Dictionary is list of word with a API for searching. 
 * The list is saved internal as a tree.
 * @see Dictionary
 * @author Volker Berlin
 */
class DictionaryFactory {

    private final Node root = new Node();
    private char[] tree;
    private int size;
    
    /**
     * Empty Constructor.
     */
    public DictionaryFactory(){
        /* empty */
    }
    
    
    /**
     * Load the directory from a compressed list of words with UTF8 encoding. The words must be delimmited with
     * newlines. This method can be called multiple times.
     * 
     * @param filename
     *            the name of the file
     * @throws IOException
     *             If an I/O error occurs.
     * @throws NullPointerException
     *             If filename is null.
     */
    public void loadWordList( URL filename ) throws IOException {
        loadWords( new WordIterator( filename ) );
    }
    
    public void loadWords( Iterator<String> words ) {
        while( words.hasNext() ) {
            String word = words.next();
            if( word != null && word.length() > 1 ) {
                add( word );
            }
        }
    }
    
    /**
     * Add a word to the tree. If it already exist then it has no effect. 
     * @param word the new word.
     */
    public void add(String word){
        Node node = root;
        for(int i=0; i<word.length(); i++){
            char c = word.charAt(i);
            NodeEntry entry = node.searchCharOrAdd( c );
            if(i == word.length()-1){
                entry.isWord = true;
                return;
            }
            Node nextNode = entry.nextNode;
            if(nextNode == null){
                node = entry.createNewNode();
            }else{
                node = nextNode;
            }
        }
    }

    /**
     * Create from the data in this factory a Dictionary object. If there 
     * are no word added then the Dictionary is empty. The Dictionary need fewer memory as the DictionaryFactory.
     * @return a Dictionary object.
     */
    public Dictionary create(){
        tree = new char[10000];
        
        root.save( this );
        
        //shrink the array
        char[] temp = new char[size];
        System.arraycopy( tree, 0, temp, 0, size );
        tree = temp;
        
        return new Dictionary(tree);
    }
    
    /**
     * Check the size of the array and resize it if needed.
     * @param newSize the required size
     */
    final void checkSize(int newSize){
        if(newSize > tree.length){
            char[] puffer = new char[Math.max(newSize, 2*tree.length)];
            System.arraycopy(tree, 0, puffer, 0, size);
            tree = puffer;
        }
    }
    
    /**
     * A node in the search tree. Every Node can include a list of NodeEnties
     */
    private final static class Node extends ArrayList<NodeEntry>{

        Node(){
            super(1);
        }
                
        NodeEntry searchCharOrAdd( char c ) {
            for(int i=0; i<size(); i++){
                NodeEntry entry = get( i );
                if(entry.c < c){
                    continue;
                }
                if(entry.c == c){
                    return entry;
                }
                entry = new NodeEntry(c);
                add( i, entry );
                trimToSize(); //reduce the memory consume, there is a very large count of this Nodes.
                return entry;
            }
            NodeEntry entry = new NodeEntry(c);
            add( entry );
            trimToSize(); //reduce the memory consume, there is a very large count of this Nodes.
            return entry;
        }
        
        int save(DictionaryFactory factory){
            int idx;
            int start = idx = factory.size;
            //reserve the needed memory
            int newSize = factory.size + size() * 3 + 1;
            factory.checkSize( newSize );
            factory.size = newSize;
            
            for(int i=0; i<size(); i++){
                NodeEntry entry = get( i );
                factory.tree[idx++] = entry.c;
                Node nextNode = entry.nextNode;
                int offset = 0;
                if(nextNode != null){
                    offset = nextNode.save(factory);
                }
                if(entry.isWord){
                    offset |= 0x80000000;
                }
                factory.tree[idx++] = (char)(offset >> 16);
                factory.tree[idx++] = (char)(offset);
            }
            factory.tree[idx] = DictionaryBase.LAST_CHAR;
            return start;
        }
    }
    
    /**
     * Described a single character in the Dictionary tree.
     */
    private final static class NodeEntry{
        final char c;
        Node nextNode;
        boolean isWord;
        
        NodeEntry(char c){
            this.c = c;
        }
        
        /**
         * Create a new Node and set it as nextNode
         * @return the nextNode
         */
        Node createNewNode() {
            return nextNode = new Node();
        }
    }
    
}
