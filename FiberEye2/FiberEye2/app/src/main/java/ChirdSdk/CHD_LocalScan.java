package ChirdSdk;

import ChirdSdk.Apis.chd_wmp_apis;
import ChirdSdk.Apis.st_SearchInfo;

import android.util.Log;

public class CHD_LocalScan {
	
	 private chd_wmp_apis  WMP	   = new chd_wmp_apis(); 
	 private st_SearchInfo DevInfo = new st_SearchInfo();
	 
	public CHD_LocalScan(){ 
		WMP.CHD_WMP_ScanDevice_Init(1); 
	}
	
	public int  getLocalScanNum(){   
		 return WMP.CHD_WMP_Scan_GetDeviceInfo(DevInfo); 	
	}
	 
	public int getLocalDevId(int Num){
		int Cnt = WMP.CHD_WMP_Scan_GetDeviceInfo(DevInfo); 
		if (Num >= Cnt)	return -1;
		 
		return DevInfo.id[Num];
	}
	 
	public String getLocalDevAlias(int Num){
		if (Num >= WMP.CHD_WMP_Scan_GetDeviceInfo(DevInfo)){
			return null;
		}

		return DevInfo.alias[Num];
	 }
	 
	public String getLocalDevAddress(int Num){
		if (Num >= WMP.CHD_WMP_Scan_GetDeviceInfo(DevInfo)){
				return null;
		}
		return DevInfo.address[Num];
	 }
		
	protected void finalize(){
		WMP.CHD_WMP_ScanDevice_UnInit();
    }
}
