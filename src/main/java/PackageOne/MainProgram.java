package PackageOne;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.sql.SQLConnection;


public class MainProgram extends AbstractVerticle{
	
	int numKiosk;
	int numPos;
	int numUsers;
	int numTransactions;
	
	static myServer server;
	static List<ServerSocket> list;
	public MainProgram(int numKiosk, int numPos, int numUsers,
			int numTransactions) {
		this.numKiosk = numKiosk;
		this.numPos = numPos;
		this.numUsers = numUsers;
		this.numTransactions = numTransactions;
	}
	
	
	public void perform(Handler<AsyncResult<Object>> next, Future<Void> fut1){
		Future<Void> f1=Future.future();
		server.getStatus(res->{
			if(res.succeeded()){
				System.out.println("done done");
				next.handle(Future.succeededFuture(res.result()));
			}else{
				System.out.println(res.cause());
				fut1.fail(res.cause());
			}
		},f1);
	}

	@Override
	public void start(Future<Void> startFuture) throws Exception {

		list=new ArrayList<ServerSocket>();
		/*InputStream input=new FileInputStream("src/main/resources/config.properties");
		Properties prop=new Properties();
		prop.load(input);
		int numKiosk=Integer.parseInt(prop.getProperty("numKiosk"));
		int numUsers=Integer.parseInt(prop.getProperty("numUsers"));
		int numPos=Integer.parseInt(prop.getProperty("numPos"));
		int numTransactions=Integer.parseInt(prop.getProperty("numTransactions"));*/
		
		
		String dbPath="jdbc:sqlite:Database/";

		String[] name_kiosk=new String[numKiosk];

		for(int i=0;i<numKiosk;i++)
			name_kiosk[i]="KIOSK "+Integer.toString(i);

		String[] name_pos=new String[numPos];

		for(int i=0;i<numPos;i++)
			name_pos[i]="POS "+Integer.toString(i);

		int[] listOfPorts_kiosk=new int[numKiosk];
		String portlist_kiosk=makePortList(listOfPorts_kiosk);
		System.out.println("kiosk port numbers "+portlist_kiosk);

		Buffer buffer=Buffer.buffer(portlist_kiosk.getBytes());

		Vertx vertx=Vertx.vertx();
		SharedData sd=vertx.sharedData();
		LocalMap<String,Buffer> map=sd.getLocalMap("portMap");
		map.put("listOfPorts_kiosk",buffer);

		int[] listOfPorts_pos=new int[numPos];
		String portlist_pos=makePortList(listOfPorts_pos);

		System.out.println("pos port numbers "+portlist_pos);

		buffer=Buffer.buffer(portlist_pos.getBytes());

		map.put("listOfPorts_pos",buffer);
		
		int serverPort=8081;
		System.out.println("MAIN SERVER PORT "+serverPort);
		
		buffer=Buffer.buffer(Integer.toString(serverPort).getBytes());
		map.put("listOfPorts_server",buffer);
		
		
		//close all opened sockets
		for(ServerSocket s:list){
			s.close();
		}
		
		System.out.println("closed sockets");
		
		TreeMap<String,Double> items=getMap();
		int len=items.size();

		String[] itemNames=new String[len];
		int index=0;
		for(String item:items.keySet())
			itemNames[index++]=item;


		LocalMap<String,Integer> map1=sd.getLocalMap("NumPorts");
		map1.put("totalPorts",0);

		myKiosk[] listOfKiosks=new myKiosk[numKiosk];



		for(int i=0;i<numKiosk;i++){
			listOfKiosks[i]=new myKiosk(listOfPorts_kiosk[i],name_kiosk[i],vertx,dbPath);
			Future<Void> fut=Future.future();
			listOfKiosks[i].startProcess(ans->{
				if(ans.succeeded()){
					System.out.println("created KIOSK");
					LocalMap<String,Integer> m=sd.getLocalMap("NumPorts");
					m.put("totalPorts",m.get("totalPorts")+1);

				}
			},fut);
		}

		myPosk[] listOfKiosks_pos=new myPosk[numPos];

		for(int i=0;i<numPos;i++){
			
			listOfKiosks_pos[i]=new myPosk(listOfPorts_pos[i],name_pos[i],vertx,dbPath);
			Future<Void> fut=Future.future();
			listOfKiosks_pos[i].startProcess(ans->{
				//System.out.println("****************************");
				
				if(ans.succeeded()){
					System.out.println("created POSK");
					LocalMap<String,Integer> m=sd.getLocalMap("NumPorts");
					m.put("totalPorts",m.get("totalPorts")+1);

				}
			},fut);
		}

	//start main server
		
		if(server==null)
		server=new myServer(serverPort,"MAIN SERVER",vertx,dbPath);
		Future<Void> fut=Future.future();
		server.startProcess(ans->{
			
			if(ans.succeeded()){
				System.out.println("created MAIN SERVER");
				LocalMap<String,Integer> m=sd.getLocalMap("NumPorts");
				m.put("totalPorts",m.get("totalPorts")+1);

			}
		},fut);
		


		vertx.executeBlocking(myFut->{

			//System.out.println("inside");

			int numStartedServers=0;


			//wait for all servers to start
			while(numStartedServers<(numKiosk+numPos+1)){
				LocalMap<String,Integer> m=sd.getLocalMap("NumPorts");
				numStartedServers=m.get("totalPorts");
			}
			//System.out.println("outside");
			myFut.complete();
		}, res->{
			if(res.succeeded()){
				System.out.println("all pos and kiosk and main server started");
				for(int i=0;i<numUsers;i++){

					int kioskNum=((int)Math.random())*(numKiosk-1);

					HttpClient client=vertx.createHttpClient();
					HttpClientRequest req=client.post(listOfPorts_kiosk[kioskNum],"localhost","/issueCard",new Handler<HttpClientResponse>(){
						public void handle(HttpClientResponse response){
							//System.out.println("Got response back "+response.getHeader("status"));
						}
					});

					double amount=500+Math.random()*500;  //minimum amount=500

					req.putHeader("amount",Double.toString(amount));
					req.putHeader("cardID",Integer.toString(i));
					req.end();
				}

				System.out.println("here......");

				for(int i=0;i<numTransactions;i++){
					int ind=(int) (Math.random()*(numPos-1));

					HttpClient client=vertx.createHttpClient();
					HttpClientRequest req=client.post(listOfPorts_pos[ind],"localhost","/transaction",new Handler<HttpClientResponse>(){
						public void handle(HttpClientResponse response){
							System.out.println("Got response back "+response.getHeader("status"));
						}
					});

					int elem=(int) (Math.random()*(itemNames.length-1));
					String item_name=itemNames[elem];
					
					int quant=(int) (Math.random()*(10));
					req.putHeader("itemName",item_name);
					req.putHeader("quantity",Integer.toString(quant));
					
					req.putHeader("amount",Double.toString(items.get(item_name)));
					int num=(int) (Math.random()*(numUsers-1));

					req.putHeader("cardID",Integer.toString(num));
					req.end();
				}
				

			}
			else
				System.out.println("not");

		});

	}

	public static String makePortList(int[] listOfPorts){
		
		StringBuilder sb=new StringBuilder("");
		for(int i=0;i<listOfPorts.length;i++){
			try {
				listOfPorts[i]=getFreePort();
			} catch (IOException e) {
				e.printStackTrace();
			}
			sb.append(Integer.toString(listOfPorts[i]));
			if(i<listOfPorts.length-1)
				sb.append(" ");
		}
		return sb.toString();
	}

	public static int getFreePort() throws IOException{
		ServerSocket socket=new ServerSocket(0);
		int port=socket.getLocalPort();
		list.add(socket);
		//socket.close();
		return port;
	}

	public static TreeMap<String,Double> getMap(){
		TreeMap<String,Double> items=new TreeMap<String,Double>();

		items.put("cup of milk", 10.0);
		items.put("Biscuits", 10.0);
		items.put("cup of tea",5.0);
		items.put("Packed lunch", 25.0);
		items.put("mineral water",12.0);
		items.put("banana",4.0);
		items.put("lunch meal",30.0);

		return items;
	}
}
