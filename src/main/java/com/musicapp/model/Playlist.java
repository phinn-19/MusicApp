package com.musicapp.model;
import java.util.ArrayList;
import java.util.List;
public class Playlist {
    private int id;
    private String name;
    private List<Song> songs;
    // contructor hiển thị trên gui
    public Playlist(int id, String name, List<Song> songs) {
        this.id = id;
        this.name = name;
        this.songs = new ArrayList<>();
    }
    // contructor để thêm mới vào playlist
    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
    }
    // contructor gom nhiều song vào 1 playlist

    public Playlist(String name, List<Song> songs) {
        this.name = name;
        this.songs = songs;
    }

    //getter và setter
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }
    //thêm/xóa bài trong playlist
    public void addSong(Song song){
        songs.add(song);
    }
    public void removeSong(Song song){
        songs.remove(song);
    }
    public int getSongCount(){
        return songs.size();
    }
    // hiển thị trong list view

    @Override
    public String toString() {
        return name + " (" + songs.size() + " bài)";
    }
}
