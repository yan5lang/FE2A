package ChirdSdk;

/**
 * Created by yan5l on 11/19/2016.
 */
public class FE_Client {

    private final static String url="192.168.100.254", key="fibereye" ,oldkey="chird";

    private static FE_Client mInstance;
    private CHD_Client client;

    public static FE_Client getInst() {
        if (mInstance == null) {
            synchronized (FE_Client.class) {
                if (mInstance == null) {
                    mInstance = new FE_Client();
                }
            }
        }
        return mInstance;
    }

    private FE_Client(){
        client = new CHD_Client();
    }

    public CHD_Client getClient(){
        if(!client.isConnect())
            client.connectDevice(url,key);
        return client;
    }
}
