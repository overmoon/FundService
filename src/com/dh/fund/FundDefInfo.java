package com.dh.fund;


public class FundDefInfo {
	private int fid;
	private String fname;
	private String fdesc;
	private String fgroup;
	private int ftype;
	private int fcountry_code;
	private int flocale_id;
	private String ftable_name;
	private String ftable_field_str;

	public int getFid() {
		return fid;
	}

	public void setFid(int fid) {
		this.fid = fid;
	}

	public String getFname() {
		return fname;
	}

	public void setFname(String fname) {
		this.fname = fname;
	}

	public String getFdesc() {
		return fdesc;
	}

	public void setFdesc(String fdesc) {
		this.fdesc = fdesc;
	}

	public String getFgroup() {
		return fgroup;
	}

	public void setFgroup(String fgroup) {
		this.fgroup = fgroup;
	}

	public int getFtype() {
		return ftype;
	}

	public void setFtype(int ftype) {
		this.ftype = ftype;
	}

	public int getFcountry_code() {
		return fcountry_code;
	}

	public void setFcountry_code(int fcountry_code) {
		this.fcountry_code = fcountry_code;
	}

	public int getFlocale_id() {
		return flocale_id;
	}

	public void setFlocale_id(int flocale_id) {
		this.flocale_id = flocale_id;
	}

	public String getFtable_name() {
		return ftable_name;
	}

	public void setFtable_name(String ftable_name) {
		this.ftable_name = ftable_name;
	}

	public String getFtable_field_str() {
		return ftable_field_str;
	}

	public void setFtable_field_str(String ftable_field_str) {
		this.ftable_field_str = ftable_field_str;
	}
	
	public boolean isSimple(FundDefInfo tmp){
		return ftable_name.equals(tmp.getFtable_name()) && !(isSupportHist()^tmp.isSupportHist());
	}
	
	public boolean isSupportHist(){
		return (ftype&0x80)>0;
	}
}
