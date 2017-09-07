package com.dh.fund;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;

/**
 * This class is a Singleton that provides access to one or many connection
 * pools defined in a Property file. A client gets access to the single instance
 * through the static getInstance() method and can then check-out and check-in
 * connections from a pool. When the client shuts down it should call the
 * release() method to close all open connections and do other clean up.
 */

public class DBPoolManager {
	// The single instance
	static private DBPoolManager instance = new DBPoolManager();
	final static private String propFileName = "dbConfig.prop";
	static private int clients;
	private PrintWriter log;
	private Hashtable pools = new Hashtable();

	/**
	 * Returns the single instance, creating one if it's the first time this
	 * method is called.
	 *
	 * @return DBConnectionManager The single instance.
	 */
	static public DBPoolManager getInstance() {
		return instance;
	}

	/**
	 * A private constructor since this is a Singleton
	 */
	private DBPoolManager() {
		init();
	}

	/**
	 * Returns a connection to the named pool.
	 *
	 * @param name
	 *            The pool name as defined in the properties file
	 * @param con
	 *            The Connection
	 */
	public void freeConnection(String name, Connection con) {
		BasicDataSource ds = (BasicDataSource) pools.get(name);
	}

	/**
	 * Returns an open connection. If no one is available, and the max number of
	 * connections has not been reached, a new connection is created.
	 *
	 * @param name
	 *            The pool name as defined in the properties file
	 * @return Connection The connection or null
	 * @throws SQLException
	 */
	public java.sql.Connection getConnection(String name) throws SQLException {
		BasicDataSource ds = (BasicDataSource) pools.get(name);
		return ds.getConnection();
	}

	/**
	 * Closes all open connections and deregisters all drivers.
	 */
	public synchronized void release() {
		// Wait until called by the last client
		if (--clients != 0) {
			return;
		}

		Enumeration allPools = pools.elements();
		while (allPools.hasMoreElements()) {
			BasicDataSource ds = (BasicDataSource) allPools.nextElement();
			try {
				ds.close();
			} catch (SQLException e) {
				log(e, "Exception when close datasource");
			}
		}

	}

	/**
	 * Creates instances of DBConnectionPool based on the properties. A
	 * DBConnectionPool can be defined with the following properties:
	 * 
	 * <PRE>
	 * <poolname>.url         The JDBC URL for the database
	 * <poolname>.user        A database user (optional)
	 * <poolname>.password    A database user password (if user specified)
	 * <poolname>.maxconn     The maximal number of connections (optional)
	 * </PRE>
	 *
	 * @param props
	 *            The connection pool properties
	 */
	private void createPools(Properties props) {
		Enumeration propNames = props.propertyNames();
		while (propNames.hasMoreElements()) {
			String name = (String) propNames.nextElement();
			if (name.contains(".db.conf")) {
				String poolName = name.substring(0, name.indexOf(".db.conf"));
				System.out.println("init pool: "+poolName);
				String configFile = props.getProperty(name);
				System.out.println("config file: "+configFile);
				if (configFile == null) {
					log("no config file for pool: " + poolName);
				}

				BasicDataSource ds = initPool(configFile);
				pools.put(poolName, ds);
			}
		}
	}

	// 根据配置文件初始化连接池
	private BasicDataSource initPool(String configFile) {
		InputStream in = null;
		try {
			in = new FileInputStream(configFile);
		} catch (FileNotFoundException e2) {
			log(e2, "Config file not found:" + configFile);
		}
		Properties properties = new Properties();
		try {
			properties.load(in);
		} catch (IOException e1) {
			log(e1, "Exception when loading config file:" + configFile);
		}

		BasicDataSource ds = null;
		try {
			ds = BasicDataSourceFactory.createDataSource(properties);
			System.out.println(ds + "aaaa");
		} catch (Exception e) {
			log(e, "Exception when create dataSource from property: "
					+ configFile);
		}
		return ds;
	}

	/**
	 * Loads properties and initializes the instance with its values.
	 */
	private void init() {
		FileInputStream inputFile;
		try {
			inputFile = new FileInputStream(propFileName);
		} catch (FileNotFoundException ex) {
			Logger.getLogger(DBPoolManager.class.getName()).log(Level.SEVERE,
					null, ex);
			ex.printStackTrace();
			return;
		}
		Properties dbProps = new Properties();
		try {
			dbProps.load(inputFile);
		} catch (Exception e) {
			System.err.println("Can't read the properties file. "
					+ "Make sure property file: " + propFileName
					+ " is in the CLASSPATH");
			return;
		}
		String logFile = dbProps.getProperty("logfile",
				"DBConnectionManager.log");
		try {
			log = new PrintWriter(new FileWriter(logFile, true), true);
		} catch (IOException e) {
			System.err.println("Can't open the log file: " + logFile);
			log = new PrintWriter(System.err);
		}
		createPools(dbProps);
	}

	/**
	 * Writes a message to the log file.
	 */
	private void log(String msg) {
		log.println(new Date() + ": " + msg);
	}

	/**
	 * Writes a message with an Exception to the log file.
	 */
	private void log(Throwable e, String msg) {
		log.println(new Date() + ": " + msg);
		e.printStackTrace(log);
	}

	/**
	 * This inner class represents a connection pool. It creates new connections
	 * on demand, up to a max number if specified. It also makes sure a
	 * connection is still open before it is returned to a client.
	 */

	public static void main(String[] args) {
		/*
		 * StringTokenizer st = new StringTokenizer(
		 * "oracle.jdbc.OracleDriver  com.mysql.jdbc.Driver"); while
		 * (st.hasMoreElements()) { String driverClassName =
		 * st.nextToken().trim(); System.out.println(driverClassName); Driver
		 * driver; try { driver = (Driver)
		 * Class.forName(driverClassName).newInstance();
		 * DriverManager.registerDriver(driver); } catch (InstantiationException
		 * | IllegalAccessException | ClassNotFoundException | SQLException e) {
		 * // TODO Auto-generated catch block e.printStackTrace(); }
		 * 
		 * }
		 */
		System.out.println("start!!!");
		try {
			Connection conn = DBPoolManager.getInstance().getConnection(
					"mysql_dh");
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select * from fundamentalInfo limit 5");
			while(rs.next()){
				String comcode = rs.getString(1);
				String secucode = rs.getString(2);
				System.out.println("comcode: "+ comcode+"; secucode: "+secucode);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			Connection conn = DBPoolManager.getInstance().getConnection(
					"oracle_gao");
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select COMCODE,SECUCODE from CENTER_ADMIN.PUB_SECURITIESMAIN where tradingcode='000001'");
			while(rs.next()){
				String comcode = rs.getString(1);
				String secucode = rs.getString(2);
				System.out.println("comcode: "+ comcode+"; secucode: "+secucode);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
