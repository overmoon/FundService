package com.dh.fund.exception;

public class SqlNullException extends NullPointerException{
	
	public SqlNullException(String str){
		super("配置文件中未有对应的sql定义，key: "+str);
	}

}
