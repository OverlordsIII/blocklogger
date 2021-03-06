package tech.dttp.block.logger.save.sql;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import tech.dttp.block.logger.util.LoggedEventType;
import tech.dttp.block.logger.util.PlayerUtils;
import tech.dttp.block.logger.util.PrintToChat;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class DbConn {
    private static Connection con = null;
    public void connect(MinecraftServer server) {
        try {
            Class.forName("org.sqlite.JDBC");
            File databaseFile;
            // Database file
            databaseFile = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "blocklogger.bl");
            con = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getPath().replace('\\', '/'));
            // Check if table exists
            ensureTable("interactions",
                    "(type STRING, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, dimension STRING NOT NULL, state STRING, player STRING, date STRING,time STRING, rolledbackat INT DEFAULT -1)");
            System.out.println("[BL] Connected to database");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void ensureTable(String name, String description) {
        // Create table if it doesn't exist
        String sql = "CREATE TABLE IF NOT EXISTS " + name + " " + description + ";";
        try {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.execute();

            System.out.println("[BL] prepared table");
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
    }

    public void writeInteractions(int x, int y, int z, BlockState state, PlayerEntity player, World world, LoggedEventType type) {
        if (con == null) {
            // Check if database isn't connected
            throw new IllegalStateException("Database connection not initialized");
        }
        //Get date
        LocalDateTime dateTime = LocalDateTime.now();
        String date = dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        try {
            // Save data
            String sql = "INSERT INTO interactions(type, x, y, z, dimension, state, player, date, time) VALUES(?,?,?,?,?,?,?,?,?)";
            PreparedStatement ps = con.prepareStatement(sql);
            // set values to insert
            ps.setString(1, type.name());
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setString(5, PlayerUtils.getPlayerDimension(player));
            //Remove { and } from the block entry
            String stateString = state.toString();
            stateString = stateString.replace("Block{", "");
            stateString = stateString.replace("}", "");
            ps.setString(6, stateString);
            ps.setString(7, getPlayerName(player));
            ps.setString(8, date);
            ps.setString(9, time);
            ps.execute();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void readEvents(int x, int y, int z, String dimension, LoggedEventType eventType, ServerCommandSource scs) {
        // Check if database is connected
        if (con == null) {
            throw new IllegalStateException("Database connection not initialized");
        }
        PreparedStatement ps;
        ResultSet rs;
        //Print initial read to chat - Blocklogger data for X, Y, Z
        try {
            Text message = new LiteralText("Blocklogger data for "+x+", "+y+", "+z+" in "+dimension);
            PlayerEntity player = scs.getPlayer();
            player.sendSystemMessage(message, scs.getPlayer().getUuid());
        } catch (CommandSyntaxException e) {
            e.printStackTrace();
        }
        try {
            //Read data
            String sql = "SELECT type,x,y,z,dimension,state,player,date,time,rolledbackat FROM interactions WHERE x=? AND y=? AND z=? AND dimension=?";
            if (eventType != null) {
                sql += " AND type=?";
            }
            ps = con.prepareStatement(sql);
            ps.setInt(1, x);
            ps.setInt(2, y);
            ps.setInt(3, z);
            ps.setString(4, dimension);
            if (eventType != null) {
                ps.setString(5, eventType.name());
            }
            rs = ps.executeQuery();
            // Repeat for every entry
            while (rs.next()) {
                //Get the info from the database and return
                //For all integers, create a String with the correct values
                x = rs.getInt("x");
                String xString = Integer.toString(x);
                y = rs.getInt("y");
                String yString = Integer.toString(y);
                z = rs.getInt("z");
                String zString = Integer.toString(z);
                String state = rs.getString("state");
                String dimensionString = rs.getString("dimension");
                String type = rs.getString("type");
                String player = rs.getString("player");
                String time = rs.getString("time");
                String date = rs.getString("date");
                String valuesArray[] = {type, xString, yString, zString, dimensionString, state, player, time, date};
                PrintToChat.prepareInteractionsPrint(valuesArray, scs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerName(PlayerEntity player) {
        // return the player's name
        Text playerText = player.getDisplayName();
        return playerText.getString();
    }

    public void close() {
        // Closes connection to database
        try {
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

	public static void readFromState(String state, ServerCommandSource scs) throws CommandSyntaxException {
        if (con == null) {
            // Check if database isn't connected
            throw new IllegalStateException("Database connection not initialized");
        }
        try{
            PreparedStatement ps = con.prepareStatement("SELECT type,x,y,z,date,time,player FROM interactions WHERE state=? AND dimension=?");
            ps.setString(1, state);
            ps.setString(2, PlayerUtils.getPlayerDimension(scs.getPlayer()));
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                String type = rs.getString(1);
                int x = rs.getInt(2);
                int y = rs.getInt(3);
                int z = rs.getInt(4);
                String date = rs.getString(5);
                String time = rs.getString(6);
                String player = rs.getString(7);
                String message = state+" was "+type+" at "+x+" "+y+" "+z+" by "+player+" at "+time+" on "+date;
                System.out.println(message);
                PrintToChat.print(scs.getPlayer(),message);
            }
        }
        catch(SQLException e){
            e.printStackTrace();
        }
	}
}
