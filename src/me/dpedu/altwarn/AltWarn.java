package me.dpedu.altwarn;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class AltWarn extends JavaPlugin implements Listener {
	/**
	 * Mysql connection
	 */
	private Connection _mysql;
	
	public void onEnable() {
		// Check the configuration
		FileConfiguration config = this.getConfig();
		if ( !config.contains( "hostname" ) ) config.set( "hostname", "localhost" );
		if ( !config.contains( "database" ) ) config.set( "database", "hardcore" );
		if ( !config.contains( "username" ) ) config.set( "username", "root" );
		if ( !config.contains( "password" ) ) config.set( "password", "" );
		this.saveConfig();
		
		// Connect to the database
		try
		{
			Class.forName( "com.mysql.jdbc.Driver" ).newInstance();
			_mysql = DriverManager.getConnection( "jdbc:mysql://" + config.getString( "hostname", "localhost" ) + "/" + config.getString( "database", "hardcore" ), config.getString( "username", "root" ), config.getString( "password", "" ) );
		} catch( Exception e ) { 
			System.err.println( "Cannot connect to database server: " + e.getMessage() );
			return;
		}
		
		// Register hooks
		this.getServer().getPluginManager().registerEvents( this, this );
	}
	
	@EventHandler( priority = EventPriority.LOW )
	public void onPlayerLogin( PlayerLoginEvent e ) {
		Player player = e.getPlayer();
		long ip = ip2int(e.getAddress());
		
		// Get a list of everyone from the player's IP
		PreparedStatement s;
		Vector<String> otherAccounts = new Vector<String>();
		try {
			s = this._mysql.prepareStatement( "SELECT * FROM `alt_ips` WHERE `ip`=?;" );
			s.setLong(1, ip);
			ResultSet rs = s.executeQuery();
			while(rs.next()) {
				otherAccounts.add(rs.getString("name"));
			}
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		// Load a list of whitelist group IDs this player is in
		Vector<Integer> groupsIn = new Vector<Integer>();
		
		PreparedStatement sCheckWhitelist;
		try {
			sCheckWhitelist = this._mysql.prepareStatement( "SELECT `groupid` FROM `alt_whitelist` WHERE `account`=?;" );
			sCheckWhitelist.setString(1, player.getName());
			ResultSet rs = sCheckWhitelist.executeQuery();
			while(rs.next()) {
				groupsIn.add(rs.getInt("groupid"));
			}
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		// For each Whitelist group someone is in,
		for(int groupId : groupsIn) {
			Vector<String> allowShared = new Vector<String>();
			PreparedStatement sGetMembers;
			try {
				// Fetch a list of members in that group
				sGetMembers = this._mysql.prepareStatement( "SELECT * FROM `alt_whitelist` WHERE `groupid`=?;" );
				sGetMembers.setInt(1, groupId);
				ResultSet rs = sGetMembers.executeQuery();
				// For every member in the group
				while(rs.next()) {
					// Remove them, if present, from the list of accounts the Player shares an IP with
					String member = rs.getString("account");
					Vector<String> toRemove = new Vector<String>();
					for(String friendAccount : otherAccounts) {
						if(friendAccount.equals(member)) {
							toRemove.add(friendAccount);
						}
					}
					for(String toRem : toRemove) {
						otherAccounts.remove(toRem);
					}
				}
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		
		if(otherAccounts.size()>0) {
			this.getServer().getScheduler().scheduleSyncDelayedTask( this, new notifyPlayer(player.getName()), 20L * 2 );
			Player[] everyone = getServer().getOnlinePlayers();
			String shareStr = "";
			for(String n : otherAccounts) {
				shareStr+=n+", ";
			}
			shareStr=shareStr.substring(0, shareStr.length()-2);
			for(Player p : everyone) {
				if(p.isOp()) {
					p.sendMessage(ChatColor.YELLOW+"["+ChatColor.RED+"AltFinder"+ChatColor.YELLOW+"]"+ChatColor.WHITE+" "+player.getName()+" shares an IP with: "+shareStr);
				}
			}
		}
	}
	
	class notifyPlayer extends BukkitRunnable {
		String name ;
		public notifyPlayer(String _name) {
			name = _name;
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			getServer().getPlayer(name).sendMessage(ChatColor.YELLOW + "" + ChatColor.MAGIC + "###############"+ChatColor.RESET+" WARNING "+ChatColor.YELLOW + "" + ChatColor.MAGIC + "###############");
			getServer().getPlayer(name).sendMessage(ChatColor.RED	 +"Our records indicate that you share an an IP ");
			getServer().getPlayer(name).sendMessage(ChatColor.RED	 +"with another player. You need to make a post");
			getServer().getPlayer(name).sendMessage(ChatColor.RED	 +"in the Shared Computers section of our forums");
			getServer().getPlayer(name).sendMessage(ChatColor.RED	 +"or you "+ChatColor.UNDERLINE+"WILL"+ChatColor.RESET+""+ChatColor.RED+" be banned for alting.");
			getServer().getPlayer(name).sendMessage(ChatColor.WHITE   +"More Info: "+ChatColor.UNDERLINE+"http://bit.ly/16Clpla");
			getServer().getPlayer(name).sendMessage(ChatColor.RED	 +"If you've already made a post, this message");
			getServer().getPlayer(name).sendMessage(ChatColor.RED	 +"can be ignored.");
			getServer().getPlayer(name).sendMessage(ChatColor.YELLOW + "" + ChatColor.MAGIC + "#######################################");
		}
		
	}
	
	public long ip2int(InetAddress theAddress) {
		long result = 0;  
		for (byte b: theAddress.getAddress()) {  
			result = result << 8 | (b & 0xFF);  
		}
		return result;
	}
}
