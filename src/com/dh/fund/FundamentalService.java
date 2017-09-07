package com.dh.fund;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.dh.fund.exception.ConnNullException;
import com.dh.fund.exception.MyException;
import com.dh.fund.exception.SqlNullException;
import com.eno.tcclient.ENORecordset;
import com.eno.tcclient.ENOResource;
import com.eno.tcclient.FieldDesc;
import com.eno.tcclient.ProPackage;
import com.eno.tcclient.TC_Service;
import com.eno.tcclient.TC_System;

/**
 * JAVA 服务示例(独立运行版本) 如果是使用容器(如：tomcat)来运行，请使用线程模式： public class Test_Service
 * extends TC_Service implements Runnable
 */
public class FundamentalService extends TC_Service {
	// 静态加载所有的类库
	// 类库需要存放在当前运行目录，或在系统中指定类库路径(windows的PATH中，linux的LD_LIBRARY_PATH中)
	static {
		try {
			System.loadLibrary("ACE");
			System.loadLibrary("CommonDll");
			System.loadLibrary("TCClient");
			System.loadLibrary("JTCClient");
		} catch (Throwable e) {
			System.out.println("WARNING: Could not load library!");
		}
	}
	private static final String mysql_pool_name = "mysql_dh";
	private static final String oracle_pool_name = "oracle_gao";
	private static final String service_prop_file = "serviceConfig.prop";
	private static final String sql_prop_file = "sqlConfig.prop";
	private static Properties sql_prop = new Properties();
	private static final int time_out = 5000;
	private static Map<String, FundDefInfo> fundMap = new HashMap<String, FundDefInfo>();

	public FundamentalService() {
		// 加入语言资源文件测试，初始化资源
		ENOResource res = ENOResource.instance();
		res.InitResource("testRes"); // 初始化资源文件
		// 初始化sql配置文件
		try {
			sql_prop.load(new FileInputStream(sql_prop_file));
		} catch (FileNotFoundException e) {
			System.out.println("未找到配置文件：" + sql_prop_file);
		} catch (IOException e) {
			System.out.println("读取配置文件出错：" + e.getMessage());
		}
		// 初始化所有基本面字段定义
		initFundDefMap();
	}

	// 初始化所有基本面对象
	private void initFundDefMap() {
		Connection conn = getConnection(mysql_pool_name, time_out);
		Statement stmt;
		try {
			stmt = conn.createStatement();
			String sql = sql_prop.getProperty("GETALLFUNDDEF");
			ResultSet rs = stmt.executeQuery(sql);
			fundMap = getFundDefFromRS(rs);
		} catch (SQLException e) {
			System.out.println("获取所有基本面字段定义失败");
			e.printStackTrace();
		} catch (MyException e) {
			System.out.println(e.getMessage());
		}
	}

	// 多线程模式下的请求处理过程
	protected void OnRequest(ProPackage pData) {
		// 处理服务监控信息，可返回一个空的数据，必须要有
		if (pData.getAppMFunc() == 0) // 监控测试请求
		{
			ENORecordset rs = new ENORecordset("Fund Service ok!", false);
			pData.setFormatType(ProPackage.DATATYPE_RECORDSET);
			// rs.NewObject("Java TC Server ok!", false);
			byte[] bResOut = rs.ExportData();
			sendResponse(pData, bResOut, (byte) 0);
			// rs.ReleaseObject();
			return;
		}

		// 其他客户请求处理
		int nClientID = pData.getClientID();
		String strData = pData.getStrData();
		log(TC_System.LOG_DEBUG, "recived user request : ClientID = "
				+ nClientID + " , Data = " + strData);

		// 设置返回的HTTP数据类型，在HTTP协议头部中
		// pData.setContentType(ProPackage.CONTENTTYPE_IMAGE_BMP);

		// 处理客户端用户功能请求，与客户端请求tc_sfuncno参数对应
		try {
			switch (pData.getAppMFunc()) {
			case 1: // 1号功能请求
			{
				break;
			}
			case 2: // 根据股票代码获取指定的基本面字段信息
			{
				long starttime = System.currentTimeMillis();
				getSpecificFileds(pData);
				long endtime = System.currentTimeMillis();
				log(TC_System.LOG_DEBUG, "获取FundInfo时间: "
						+ (endtime - starttime));
				break;
			}
			case 3: // 根据股票代码和日期获取指定的权息数据
			{

				long starttime = System.currentTimeMillis();
				getCCAInfo(pData);
				long endtime = System.currentTimeMillis();
				log(TC_System.LOG_DEBUG, "获取CCAInfo时间: "
						+ (endtime - starttime));
				break;
			}
			default: // 其他功能请求
			{
				String strErr = "不支持的请求功能!";
				sendErrResponse(pData, strErr, 0);
			}
				break;
			}
		} catch (Exception e) {
			sendErrResponse(pData, e.getMessage(), 0);
		}
	}

	// 根据股票代码和日期获取指定的权息数据
	private void getCCAInfo(ProPackage pData) throws MyException {
		String strData = pData.getStrData();
		String[] data = strData.split("&");
		// 获取请求中的股票代码和日期
		String codeStr = "", dateStr = "";
		try {
			for (int i = 0; i < data.length; i++) {
				if (data[i].contains("code")) {
					codeStr = data[i].split("=")[1].trim();
				} else if (data[i].contains("date")) {
					dateStr = data[i].split("=")[1].trim();
				}
			}
		} catch (Exception e) {
			log(TC_System.LOG_ERROR, "参数不符合要求: " + strData);
			sendErrResponse(pData, "参数不符合要求: " + strData, 0);
			return;
		}
		// 没有获取到股票代码或者基本面字段，返回错误
		if (codeStr.equals("") || dateStr.equals("")) {
			String errStr = "缺少参数，未找到证券代码或日期字段: " + strData;
			log(TC_System.LOG_DEBUG, errStr);
			sendErrResponse(pData, errStr, 0);
			return;
		} else {
			// 查询CCA信息
			ENORecordset rs = queryCCA(codeStr, dateStr);
			// 输出字节数据
			byte[] bResOut = rs.ExportData();
			// 设置返回的数据类型
			pData.setFormatType(ProPackage.DATATYPE_RECORDSET);
			// 返回结果给客户端
			sendResponse(pData, bResOut, (byte) 0);
		}
	}

	// 查询cca信息
	private ENORecordset queryCCA(String codeStr, String dateStr)
			throws MyException {
		Object[] codes = getCode(codeStr);
		String secucode = (String) codes[1];
		String date = "";
		String queryCCASqlStr = sql_prop.getProperty("GETCCAINFO");
		if (queryCCASqlStr == null) {
			throw new SqlNullException("GETCCAINFO");
		}
		if (dateStr.equals("0")) {
			date = "00010101";
		} else {
			date = dateStr;
		}
		queryCCASqlStr = queryCCASqlStr.replace(":secucode", secucode);
		queryCCASqlStr = queryCCASqlStr.replace(":date", date);
		ENORecordset rset = null;
		Connection conn = null;
		Statement stmt = null;
		ResultSet r = null;
		try {
			conn = getConnection(oracle_pool_name, time_out);
			stmt = conn.createStatement();
			log(TC_System.LOG_DEBUG, "执行sql: " + queryCCASqlStr);
			r = stmt.executeQuery(queryCCASqlStr);
			rset = getCCAENORecordset(r);
		} catch (SQLException e) {
			log(TC_System.LOG_ERROR, "SQL查询CCA出错: ", e);
			throw new MyException("SQL查询CCA出错");
		} finally {
			freeConnection(r, stmt, conn);
		}
		return rset;
	}

	// 将获取的CCA结果封装成eno结果集
	private ENORecordset getCCAENORecordset(ResultSet rs) throws MyException {
		ENORecordset rset = new ENORecordset();
		// 添加字段
		rset.InsertField("cca_date", FieldDesc.fdtype_int4, -1);
		rset.InsertField("dividend_ratio", FieldDesc.fdtype_real4, -1);
		rset.InsertField("dividend", FieldDesc.fdtype_real4, -1);
		rset.InsertField("distribution_ratio", FieldDesc.fdtype_real4, -1);
		rset.InsertField("distribution", FieldDesc.fdtype_real4, -1);

		try {
			// 添加记录
			while (rs.next()) {
				rset.InsertRecord(-1, 1);
				rset.UpdateRecord(0, rs.getInt("cca_date"));
				rset.UpdateRecord(1, rs.getFloat("dividend_ratio"));
				rset.UpdateRecord(2, rs.getFloat("dividend"));
				rset.UpdateRecord(3, rs.getFloat("distribution_ratio"));
				rset.UpdateRecord(4, rs.getFloat("distribution"));
			}
		} catch (SQLException e) {
			log(TC_System.LOG_ERROR, "封装CCA结果数据出错: ", e);
			throw new MyException("SQL封装CCA结果数据出错");
		}
		return rset;
	}

	// 根据股票代码获取指定的基本面字段信息
	private void getSpecificFileds(ProPackage pData) throws Exception {
		// TODO Auto-generated method stub
		String strData = pData.getStrData();
		String[] data = strData.split("&");
		// 获取请求中的股票代码和指定的基本面字段
		String codeStr = "", fieldStr = "";
		try {
			for (int i = 0; i < data.length; i++) {
				if (data[i].contains("code")) {
					codeStr = data[i].split("=")[1].trim();
				} else if (data[i].contains("fields")) {
					fieldStr = data[i].split("=")[1].trim();
				}
			}
		} catch (Exception e) {
			log(TC_System.LOG_ERROR, "参数不符合要求: " + strData);
			sendErrResponse(pData, "参数不符合要求: " + strData, 0);
			return;
		}
		// 没有获取到股票代码或者基本面字段，返回错误
		if (codeStr.equals("") || fieldStr.equals("")) {
			String errStr = "缺少参数，未找到证券代码或基本面字段: " + strData;
			log(TC_System.LOG_DEBUG, errStr);
			sendErrResponse(pData, errStr, 0);
			return;
		} else {
			ENORecordset[] rss = null;
		
			String[] fields = fieldStr.split(",");
			// 基本面字段分组
			ArrayList<ArrayList<FundDefInfo>> fundDefGroups = getFundDefGroups(fields);
			// 从港澳数据库获取基本面数据
			rss = getFundInfos(fundDefGroups, codeStr);

			// 输出字节数据
			byte[] bResOut = ENORecordset.ExportMRData(rss);
			// 设置返回的数据类型
			pData.setFormatType(ProPackage.DATATYPE_RECORDSET);
			// 返回结果给客户端
			sendResponse(pData, bResOut, (byte) 0);
		}

	}

	
	/*
	 * 获取基本面数据 fundDefGroups: 分组数据 code: 证券交易代码
	 */
	private ENORecordset[] getFundInfos(
			ArrayList<ArrayList<FundDefInfo>> fundDefGroups, String codeStr)
			throws Exception {
		ArrayList<ENORecordset> rsSet = new ArrayList<ENORecordset>();

		Object[] codes = getCode(codeStr);
		int comcode = (int) codes[0];
		String secucode = (String) codes[1];
		Connection conn = null;
		Statement stmt = null;
		ResultSet r = null;
		try {
			conn = getConnection(oracle_pool_name, time_out);
			stmt = conn.createStatement();
			for (ArrayList<FundDefInfo> array : fundDefGroups) {
				String tableName = array.get(0).getFtable_name();
				String sql = sql_prop.getProperty(tableName);
				if (sql == null) {
					throw new SqlNullException(tableName);
				}

				// 需要获取的字段
				String fields = "";
				for (FundDefInfo def : array) {
					fields += (def.getFtable_field_str() + ",");
				}
				fields = fields.substring(0, fields.length() - 1);
				sql = sql.replace(":comcode", String.valueOf(comcode));
				sql = sql.replace(":secucode", secucode);
				sql = sql.replace(":table", tableName);
				sql = sql.replace(":fields", fields);
				log(TC_System.LOG_DEBUG, "执行sql: " + sql);
				// 执行查询
				r = stmt.executeQuery(sql);
				// 封装成结果集
				ENORecordset ers = getFundENORecordset(array, r);
				rsSet.add(ers);
			}
		} catch (SQLException e) {
			log(TC_System.LOG_ERROR, "获取基本面数据出错: ", e);
			throw new MyException("SQL获取基本面数据出错");
		} catch (MyException me) {
			throw me;
		} finally {
			freeConnection(r, stmt, conn);
		}
		return rsSet.toArray(new ENORecordset[] {});
	}

	// 获取公司/证券代码
	private Object[] getCode(String codeStr) throws MyException {
		// 获取股票代码中可能存在的市场代码，如：000001.1
		String[] tmp = codeStr.split("\\.");
		String code = tmp[0];
		String exchangeStr = "";
		if (tmp.length > 1) {
			exchangeStr = tmp[1];
		} else {
			String str = "未包含市场代码：" + codeStr;
			log(TC_System.LOG_DEBUG, str);
		}

		String comsql = sql_prop.getProperty("GETCODE");
		if (comsql == null) {
			throw new SqlNullException("GETCODE");
		}

		String exchange = "";
		if (exchangeStr != "" && exchangeStr != null) {
			// 查询加入市场代码
			String exchangeCode = sql_prop.getProperty("Market." + exchangeStr);
			if (exchangeCode != null) {
				exchange = "ExchangeCode=" + exchangeCode;
			} else {
				log(TC_System.LOG_DEBUG, "未定义的市场代码：" + exchangeStr);
			}
		} else {
			exchange = "1=1";
		}
		comsql = comsql.replace(":exchange", exchange);
		int comcode = 0;
		String secucode = "";
		Connection conn = getConnection(oracle_pool_name, time_out);
		Statement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.createStatement();
			// 替换证券交易代码占位符
			comsql = comsql.replace(":code", code);
			log(TC_System.LOG_DEBUG, "执行sql: " + comsql);
			rs = stmt.executeQuery(comsql);
			// 获取公司编码
			if (rs.next()) {
				comcode = rs.getInt("comcode");
				secucode = rs.getString("secucode");
			} else {
				log(TC_System.LOG_DEBUG, "无效的代码：" + code + "." + exchangeStr);
				throw new MyException("无效的代码：" + code + "." + exchangeStr);
			}
		} catch (SQLException e) {
			log(TC_System.LOG_ERROR, "获取公司代码出错: ", e);
			throw new MyException("SQL获取公司代码出错");
		} finally {
			freeConnection(rs, stmt, conn);
		}
		return new Object[] { comcode, secucode };
	}

	// 将基本面sql数据结果封装成ENO结果集
	private ENORecordset getFundENORecordset(ArrayList<FundDefInfo> array,
			ResultSet rs) throws MyException {
		ENORecordset rset = new ENORecordset();
		// 添加字段
		rset.InsertField("fund_date", FieldDesc.fdtype_string, -1);
		rset.InsertField("period_date", FieldDesc.fdtype_string, -1);
		rset.InsertField("record_date", FieldDesc.fdtype_string, -1);
		for (FundDefInfo def : array) {
			rset.InsertField(def.getFname(), FieldDesc.fdtype_string, -1);
		}
		try {
			// 添加记录
			while (rs.next()) {
				rset.InsertRecord(-1, 1);
				rset.UpdateRecord(0, rs.getString("fund_date"));
				rset.UpdateRecord(1, rs.getString("period_date"));
				rset.UpdateRecord(2, rs.getString("record_date"));
				int size = array.size();
				for (int i = 0; i < size; i++) {
					rset.UpdateRecord(3 + i,
							rs.getString(array.get(i).getFtable_field_str()));
				}
			}
		} catch (SQLException e) {
			log(TC_System.LOG_ERROR, "封装基本面数据出错: ", e);
			throw new MyException("SQL封装基本面数据出错");
		}
		return rset;
	}

	private void log(int level, String str) {
		TC_System.LOG(level, "(" + getCurrentTime() + ") " + str + "\r\n");
	}

	private void log(int level, String str, Exception e) {
		TC_System.LOG(level, "(" + getCurrentTime() + ") " + str
				+ getErrorInfoFromException(e));
	}

	// 释放数据库连接
	private void freeConnection(ResultSet rs, Statement stmt, Connection conn) {
		try {
			if (rs != null) {
				rs.close();
			}
			if (stmt != null) {
				stmt.close();
			}
			if (conn != null) {
				conn.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// 获取数据库连接
	private Connection getConnection(String poolName, int i) {
		Connection conn = null;
		try {
			conn = DBPoolManager.getInstance().getConnection(poolName);
		} catch (Exception e) {
			log(TC_System.LOG_ERROR, "获取连接出错", e);
		}
		if (conn == null) {
			throw new ConnNullException("连接为空，连接池：" + poolName);
		}
		return conn;
	}

	// 获取当前时间
	private String getCurrentTime() {
		String temp_str = "";
		Date dt = new Date();
		// 最后的aa表示“上午”或“下午” HH表示24小时制 如果换成hh表示12小时制
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		temp_str = sdf.format(dt);
		return temp_str;
	}

	private ArrayList<ArrayList<FundDefInfo>> getFundDefGroups(String[] fields) {
		ArrayList<ArrayList<FundDefInfo>> arrayList = new ArrayList<ArrayList<FundDefInfo>>();
		for (String str : fields) {
			FundDefInfo defInfo = fundMap.get(str);
			if(defInfo == null){
				log(TC_System.LOG_DEBUG, "未定义的基本面字段："+str);
				continue;
			}
			int size = arrayList.size();
			boolean hasSame = false;
			for (int i = 0; i < size; i++) {
				FundDefInfo tmp = arrayList.get(i).get(0);
				// 判断是否同组
				if (defInfo.isSimple(tmp)) {
					arrayList.get(i).add(defInfo);
					hasSame = true;
					break;
				}
			}
			// 如果没有类似的，则增加一组
			if (!hasSame) {
				arrayList.add(new ArrayList<FundDefInfo>() {
					{
						add(defInfo);
					}
				});
			}
		}
		return arrayList;
	}

	// 从结果集获取基本面字段定义
	private Map<String, FundDefInfo> getFundDefFromRS(ResultSet rs)
			throws MyException {
		Map<String, FundDefInfo> fundMap = new HashMap<String, FundDefInfo>();
		try {
			while (rs.next()) {
				FundDefInfo fundDefInfo = new FundDefInfo();
				fundDefInfo.setFid(rs.getInt("fid"));
				fundDefInfo.setFname(rs.getString("fname"));
				fundDefInfo.setFdesc(rs.getString("fdesc"));
				fundDefInfo.setFgroup(rs.getString("fgroup"));
				fundDefInfo.setFtype(rs.getInt("ftype"));
				fundDefInfo.setFcountry_code(rs.getInt("fcountry_code"));
				fundDefInfo.setFlocale_id(rs.getInt("flocale_id"));
				fundDefInfo.setFtable_name(rs.getString("ftable_name"));
				fundDefInfo.setFtable_field_str(rs
						.getString("ftable_field_str"));
				fundMap.put(fundDefInfo.getFname(), fundDefInfo);
			}
		} catch (SQLException e) {
			log(TC_System.LOG_ERROR, "从结果集获取基本面字段定义失败", e);
			throw new MyException("从结果集获取基本面字段定义失败");
		}
		return fundMap;
	}

	public static String getErrorInfoFromException(Exception e) {
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			return "\r\n" + sw.toString() + "\r\n";
		} catch (Exception e2) {
			return "bad getErrorInfoFromException";
		}
	}

	// 返回错误信息
	private int sendErrResponse(ProPackage pData, String errStr, int errCode) {
		// 设置返回的数据类型
		pData.setFormatType(ProPackage.DATATYPE_RECORDSET);
		ENORecordset rs = new ENORecordset();
		rs.SetErrorInfo();
		rs.InsertField("error", FieldDesc.fdtype_string, "error info",
				pData.IsUnicodeData(), -1);
		// 插入数据记录
		rs.InsertRecord(-1, 1);
		rs.UpdateRecord(0, errStr);
		byte[] bResout = rs.ExportData();

		// 返回结果给客户端（错误标识号）
		return sendResponse(pData, bResout, (byte) errCode);
	}

	// 获取该Service的详细信息，pszReq为有关请求参数
	protected String GetServiceInfo(String pszReq) {
		// 可根据pszReq参数来返回不同的服务信息
		return "fundamental service";
	}

	// 可带启动参数，如: java com.eno.test.Test_Service -TC 127.0.0.1:9000 -LANG
	// zh_CN.GBK -DEBUG 3 -LOG 3
	public static void main(String args[]) {
		// 初始化系统环境
		TC_System.initEnv();
		// 设置语言种类(从启动参数中获取)
		// String sLocale = TC_System.getOptValue(args, "LANG", "chs");
		// TC_System.setLocale(sLocale);

		// 由操作系统类型来设置地域编码类型
		String os = System.getProperty("os.name");
		if (os.toLowerCase().startsWith("win")) {
			TC_System.setLocale("chs"); // Windows系统
		} else {
			TC_System.setLocale("zh_CN.GBK"); // Linux系统
		}

		String strTC = null, sPath = "logs", logName = null;
		String uid = "Fund_Service", pwd = "";
		int nDebug = 3, nLog = 3, serviceID = 402, serviceWeight = 1;
		if (args.length > 0) {
			// 取连接的TC地址及端口
			strTC = TC_System.getOptValue(args, "TC", "127.0.0.1:9000");

			// 设置日志文件参数(从启动参数中获取)
			nDebug = Integer
					.parseInt(TC_System.getOptValue(args, "DEBUG", "0"));
			nLog = Integer.parseInt(TC_System.getOptValue(args, "LOG", "2"));
			sPath = TC_System.getOptValue(args, "LOGDIR", sPath);
		} else {
			Properties prop = new Properties();
			try {
				prop.load(new FileInputStream(service_prop_file));
			} catch (FileNotFoundException e1) {
				System.out.println("未找到配置文件：" + service_prop_file);
			} catch (IOException e1) {
				System.out.println("读取配置文件出错：" + getErrorInfoFromException(e1));
			}

			strTC = prop.getProperty("TC_URL", "127.0.0.1:9000");
			nDebug = Integer.parseInt(prop.getProperty("DEBUG", "0"));
			serviceID = Integer.parseInt(prop.getProperty("SERVICE_ID", "402"));
			serviceWeight = Integer.parseInt(prop.getProperty("SERVICE_WEIGHT",
					"1"));
			nLog = Integer.parseInt(prop.getProperty("LOG", "2"));
			logName = prop.getProperty("LOGNAME", "Fund_Service");
			sPath = prop.getProperty("LOGDIR ", sPath);
			uid = prop.getProperty("UID", "Fund_Service");
			pwd = prop.getProperty("PWD", "");
		}

		TC_System.setLogMsgBackend(logName, nDebug, nLog, sPath);

		// 启动异步事件处理机制
		TC_System.startEvent(true);

		// 具体服务实列化和处理
		FundamentalService test = new FundamentalService();
		// test.NewObject();

		// 注册服务到TC，nServiceID（108）为服务ID，与客户端请求tc_mfuncno参数对应
		if (test.registerService(strTC, uid, pwd, serviceID, serviceWeight) == 0) {
			// test.ReleaseObject(); //释放服务对象

			TC_System.LOG(TC_System.LOG_ERROR,
					"Can't connect to the ETC server!\r\n");
		} else {
			TC_System.LOG(TC_System.LOG_OTHER, "(%D) Services startup!\n");

			// 等待事件并处理，直到用户按Ctrl+C中断
			TC_System.waitEvent();

			test.closeService(); // 停止服务

			TC_System.LOG(TC_System.LOG_OTHER, "(%D) Services shutdown!\n");
		}

		// 释放服务本地对象
		test.ReleaseObject();

		// 停止异步事件处理机制
		TC_System.stopEvent();

		// 停止日志记录
		TC_System.stopLogMsgBackend();

		// 系统环境清理
		TC_System.finiEnv();
	}
}
