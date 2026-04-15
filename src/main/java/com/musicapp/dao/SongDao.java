package com.musicapp.dao;
import com.musicapp.database.DatabaseConnection;
import com.musicapp.model.Song;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
public class SongDao {
    // lấy tất cả bài hát
    public List<Song> getAllSongs(){
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT * FROM songs";

        try (Statement stmt = DatabaseConnection.getConnection().createStatement(); ResultSet rs = stmt.executeQuery(sql)){
            //duyệt qua từng hàng của table
            while(rs.next()){
                songs.add(new Song (
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("album"),
                        rs.getInt("duration"),
                        rs.getString("file_path")

                ));
            }
        }
        catch (SQLException e){
            System.out.println("Lỗi lấy danh sách bài hát: " + e.getMessage());
        }
        return songs;
    }
    // search song theo tên hoặc ca sĩ
    public List<Song> searchSongs(String keyword){
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT *FROM songs WHERE title LIKE ? OR artist LIKE ?";

        try(PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)){
            stmt.setString(1, "%" + keyword + "%");
            stmt.setString(2, "%" + keyword + "%");
            ResultSet rs = stmt.executeQuery();

            while(rs.next()){
                songs.add(new Song(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("album"),
                        rs.getInt("duration"),
                        rs.getString("file_path")
                ));
            }
        }
        catch (SQLException e){
            System.out.println("Lỗi tìm kiếm" + e.getMessage());
        }
        return songs;
    }
    
    // kiểm tra bài hát đã tồn tại chưa
    public boolean songExists(Song song) {
        String sql = "SELECT COUNT(*) FROM songs WHERE title = ? AND artist = ? AND file_path = ?";
        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            stmt.setString(1, song.getTitle());
            stmt.setString(2, song.getArtist());
            stmt.setString(3, song.getFilePath());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.out.println("Lỗi kiểm tra bài hát tồn tại: " + e.getMessage());
        }
        return false;
    }
    
    // thêm mới bài hát
    public boolean addSong(Song song){
        // Kiểm tra trùng trước khi thêm
        if (songExists(song)) {
            System.out.println("Bài hát đã tồn tại: " + song.getTitle() + " - " + song.getArtist());
            return false;
        }
        
        String sql = "INSERT INTO songs (title, artist, album, duration, file_path) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
            stmt.setString(1, song.getTitle());
            stmt.setString(2, song.getArtist());
            stmt.setString(3, song.getAlbum());
            stmt.setInt(4, song.getDuration());
            stmt.setString(5, song.getFilePath());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if(rs.next()){
                song.setId(rs.getInt(1));
            }
            return true;
        }
        catch (SQLException e){
            System.out.println("Lỗi thêm bài hát" + e.getMessage());
        }
        return false;
    }
    // xoá bài hát theo id
    public boolean deleteSong(int id) {
        String sql = "DELETE FROM songs WHERE id = ?";

        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Lỗi xóa bài hát: " + e.getMessage());
            return false;
        }
    }
    
    // xóa các bài hát trùng trong database
    public int removeDuplicates() {
        int deletedCount = 0;
        // Use JOIN approach for MySQL compatibility
        String sql = "DELETE s1 FROM songs s1 " +
                    "INNER JOIN songs s2 ON " +
                    "s1.title = s2.title AND " +
                    "s1.artist = s2.artist AND " +
                    "s1.file_path = s2.file_path AND " +
                    "s1.id > s2.id";
        
        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            deletedCount = stmt.executeUpdate();
            System.out.println("Đã xóa " + deletedCount + " bài hát trùng");
        } catch (SQLException e) {
            System.out.println("Lỗi xóa bài hát trùng: " + e.getMessage());
        }
        
        return deletedCount;
    }

    // Sửa thông tin bài hát
    public boolean updateSong(Song song) {
        String sql = "UPDATE songs SET title=?, artist=?, album=?, duration=?, file_path=? WHERE id=?";

        try (PreparedStatement stmt = DatabaseConnection.getConnection().prepareStatement(sql)) {
            stmt.setString(1, song.getTitle());
            stmt.setString(2, song.getArtist());
            stmt.setString(3, song.getAlbum());
            stmt.setInt(4, song.getDuration());
            stmt.setString(5, song.getFilePath());
            stmt.setInt(6, song.getId());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Lỗi sửa bài hát: " + e.getMessage());
            return false;
        }
    }

}
