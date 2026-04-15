package com.musicapp.dao;

import com.musicapp.database.DatabaseConnection;
import com.musicapp.model.Playlist;
import com.musicapp.model.Song;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PlaylistDao {

    // Lấy tất cả playlist
    public List<Playlist> getAllPlaylists() {
        List<Playlist> playlists = new ArrayList<>();
        String sql = "SELECT * FROM playlists";

        try (Statement stmt = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                playlists.add(new Playlist(
                        rs.getInt("id"),
                        rs.getString("name"),
                        new ArrayList<>()  // FIX: constructor cần 3 tham số
                ));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy danh sách playlist: " + e.getMessage());
        }
        return playlists;
    }

    // Tạo playlist mới
    public boolean addPlaylist(Playlist playlist) {
        String sql = "INSERT INTO playlists (name) VALUES (?)";

        try (PreparedStatement stmt = DatabaseConnection.getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, playlist.getName());
            stmt.executeUpdate();

            // Lấy id DB vừa tạo, gán lại vào object
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                playlist.setId(rs.getInt(1));
            }
            return true;

        } catch (SQLException e) {
            System.out.println("Lỗi tạo playlist: " + e.getMessage());
            return false;
        }
    }

    // Xóa playlist
    public boolean deletePlaylist(int id) {
        String sql = "DELETE FROM playlists WHERE id = ?";

        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Lỗi xóa playlist: " + e.getMessage());
            return false;
        }
    }

    // Lấy tất cả bài hát trong một playlist
    public List<Song> getSongsInPlaylist(int playlistId) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT s.* FROM songs s " +
                "JOIN playlist_songs ps ON s.id = ps.song_id " +
                "WHERE ps.playlist_id = ? " +
                "ORDER BY ps.position";

        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, playlistId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                songs.add(new Song(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("album"),
                        rs.getInt("duration"),
                        rs.getString("file_path")
                ));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi lấy bài hát trong playlist: " + e.getMessage());
        }
        return songs;
    }

    // Thêm bài hát vào playlist
    public boolean addSongToPlaylist(int playlistId, int songId) {
        // Lấy position cuối cùng trong playlist
        String sqlPos = "SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_songs WHERE playlist_id = ?";
        String sqlInsert = "INSERT INTO playlist_songs (playlist_id, song_id, position) VALUES (?, ?, ?)";

        // FIX: dùng try-with-resources để đảm bảo đóng statement và ResultSet
        try (PreparedStatement stmtPos = DatabaseConnection.getConnection().prepareStatement(sqlPos)) {
            stmtPos.setInt(1, playlistId);
            ResultSet rs = stmtPos.executeQuery();
            int position = rs.next() ? rs.getInt(1) : 1;
            rs.close();

            try (PreparedStatement stmtInsert = DatabaseConnection.getConnection().prepareStatement(sqlInsert)) {
                stmtInsert.setInt(1, playlistId);
                stmtInsert.setInt(2, songId);
                stmtInsert.setInt(3, position);
                stmtInsert.executeUpdate();
                return true;
            }

        } catch (SQLException e) {
            System.out.println("Lỗi thêm bài vào playlist: " + e.getMessage());
            return false;
        }
    }

    // Xóa bài hát khỏi playlist
    public boolean removeSongFromPlaylist(int playlistId, int songId) {
        String sql = "DELETE FROM playlist_songs WHERE playlist_id = ? AND song_id = ?";

        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, playlistId);
            stmt.setInt(2, songId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Lỗi xóa bài khỏi playlist: " + e.getMessage());
            return false;
        }
    }
}