package com.musicapp.model;

public class Song {
    private int id;
    private String title;
    private String artist;
    private String album;

    private int duration;
    private String filePath;

    // hàm khởi tạo (contructor)
    // hiển thị ra GUI (lấy từ DB ra)
    public Song(int id, String title, String artist, String album, int duration, String filePath) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.filePath = filePath;
    }
    // thêm mới bài hát
    public Song(String title, String artist, String album, int duration, String filePath) {
        this.title = title; // gán giá trị mà user truyền vào (tham số) cho thuộc tính.
        this.artist = artist;// this = chính thuộc tính đó.
        this.album = album; // không dùng album = album -> null
        this.duration = duration;
        this.filePath = filePath;
    }
    // lấy và xem dữ liệu = getter
    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public int getDuration() {
        return duration;
    }

    public String getFilePath() {
        return filePath;
    }
    // setter = sửa dữ liệu
    // thường sẽ không setter ở id, tuy nhiên vì khi obj mới dc tạo ra -> db gán id nhưng obj kh biết
    // dùng setter id trong DAO để đồng bộ (Data Access Object)
    public void setId(int id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    // lưu trong db là giây -> hiển thị ra gui = phút:giây
    public String getDurationFormatted(){
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
    // hiển thị trong listView
    @Override
    public String toString() {
        return title + " - " + artist;
    }
}
