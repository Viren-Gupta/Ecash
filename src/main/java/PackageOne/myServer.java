package PackageOne;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.core.AsyncResult;


public class myServer{
	private int portNum;
	private String name;
	private SQLConnection conn;
	private Vertx vertx;
	private String dbPath;
	
	private int counter;
	
		public myServer(int portNum, String name,Vertx vertx,String dbPath) {
		this.portNum = portNum;
		this.name = name;
		this.vertx=vertx;
		this.dbPath=dbPath;
		this.counter=counter;
	}
		
	List<Object> temp_list;	
	public void establishConnection(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut) throws IOException {
		String url=dbPath+"file_"+Integer.toString(this.portNum)+"_"+counter+".db";
		counter++;

		//System.out.println(url);
		JsonObject config=new JsonObject();
		config.put("url",url);
		config.put("driver_class","org.sqlite.JDBC");
		JDBCClient jdbc = JDBCClient.createNonShared(vertx,config);

		jdbc.getConnection(ar -> {
			if (ar.failed()) {
				System.out.println(ar.cause());
				fut.fail(ar.cause());
			} else {
				next.handle(Future.succeededFuture(ar.result()));
			}
		});
	}

	public void initializeDB(AsyncResult<SQLConnection> result,Handler<AsyncResult<Void>> next, Future<Void> fut){
		if(result.failed())
			fut.fail(result.cause());
		else{
			this.conn=result.result();
			conn.execute("CREATE TABLE IF NOT EXISTS CBR(ui INTEGER AUTO_INCREMENT,id INTEGER,transNum INTEGER"
					+ ",location VARCHAR(100),amount double,timeOfTransaction VARCHAR(100),"
					+ "itemName VARCHAR(200),quantity INTEGER,primary key(ui))",
					ar->{
						if(ar.failed()){
							System.out.println("error");
							System.out.println(ar.cause());
							fut.fail(ar.cause());
							return;
						}else{

							conn.execute("CREATE TABLE IF NOT EXISTS Balance_reg(ui INTEGER AUTO_INCREMENT,id INTEGER,balance "
									+ "double,primary key(ui))",ar1->{
										if(ar1.succeeded()){
											System.out.println("db created successfully");
											next.handle(Future.<Void>succeededFuture());
											return;
										}
										else{
											System.out.println("db for server creation failed");
											fut.fail(ar.cause());
											return;
										}
									});

						}
					}
					);
		}
	}


	MyCard temp;
	public void createServer(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut){
		HttpServer server=vertx.createHttpServer();
		server.requestHandler(request->{
			String p=request.path();

			if(p.equals("/update")){
				int senderPort=Integer.parseInt(request.getHeader("sender"));
				//System.out.println(this.portNum+" received update POS request from "+senderPort);

				Future<Void> fut_a=Future.future();


				updateDatabase_fromKiosk(result->{
					if(result.succeeded()){
						
						//System.out.println("update pos successfully on request sent from "+senderPort);
					}
					else
						System.out.println("update failed on request sent from "+senderPort);
				},
				Integer.parseInt(request.getHeader("cardNum"))
				, Double.parseDouble(request.getHeader("balance")),
				fut_a);
			}

			if(p.equals("/getAnswer")){
				
				System.out.println("/**************************");
				Future<Void> f1=Future.future();
				
				getTopSellingItems(res->{
					if(res.succeeded()){
						System.out.println("quantu");
						
						String d=parseItems(temp_list);
						
						request.response()
					      .putHeader("content-type", "text/plain")
					      .putHeader("Access-Control-Allow-Origin", "*")
					      .putHeader("Access-Control-Allow-Methods","GET, POST, OPTIONS")
					      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
					      .end(d);
						
					}else{
						System.out.println(res.cause());
					}
				},f1);
			}
			
			if(p.equals("/getAnswer1")){
				
				System.out.println("/**************************");
				Future<Void> f1=Future.future();
				
				getTopSellingPOS(res->{
					if(res.succeeded()){
						System.out.println("--------------SERVER OUTPUT------------------------");
						
						String d=parseItems_pos(temp_list);
						
						request.response()
					      .putHeader("content-type", "text/plain")
					      .putHeader("Access-Control-Allow-Origin", "*")
					      .putHeader("Access-Control-Allow-Methods","GET, POST, OPTIONS")
					      .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
					      .end(d);
						
					}else{
						System.out.println(res.cause());
					}
				},f1);
			}
			
			if(p.equals("/update_pos")){
				int senderPort=Integer.parseInt(request.getHeader("sender"));
			//	System.out.println(this.portNum+" received update request from POS "+senderPort);

						
				long timeID=vertx.setPeriodic(2000,new Handler<Long>(){

					@Override
					public void handle(Long arg0) {
						//System.out.println("fired->>>>>>> "+arg0);

						conn.query("select * from Balance_reg where id="+Integer.parseInt(request.getHeader("cardNum")),select->{
							if(select.succeeded()){
								int rows=select.result().getNumRows();
								if(rows==1){
									Future<Void> fut_a=Future.future();

									updateMyRegister(result->{
										if(result.succeeded()){
											//System.out.println("update successfully on request sent from "+senderPort);
											vertx.cancelTimer(arg0);

										}
										else
											System.out.println("update failed on request sent from "+senderPort);
									},
									Integer.parseInt(request.getHeader("cardNum"))
									, request.getHeader("location"),Double.parseDouble(request.getHeader("amount")),
									request.getHeader("date"),request.getHeader("itemName"),Integer.parseInt(request.getHeader("quantity")),fut_a);

									
								}
							}else{
								System.out.println("failed select");
							}
						});

					}

				});
				
			
			}

		});


		server.listen(this.portNum,"localhost",res->{
			if(res.succeeded()){
				next.handle(Future.succeededFuture());
				System.out.println("Started server on port "+this.portNum);
			}
			else
				System.out.println("Failed starting server on port ");
		});
	}

	protected void getTopSellingItems(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut) {
		temp_list=new ArrayList<Object>();
		
		String query="select itemName,count(quantity) from CBR group by itemName";
		this.conn.query(query,res->{
			if(res.succeeded()){
				//System.out.println("QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ");
				//System.out.println("select1");
				int numItems=res.result().getNumRows();
				Sample itemsArray[]=new Sample[numItems];
				int index=0;
				for(JsonObject obj:res.result().getRows()){
					itemsArray[index++]=new Sample((String)obj.getValue("itemName"),(int)obj.getValue("count(quantity)"));
				}
				
				Arrays.sort(itemsArray,new Comparator<Sample>(){

					@Override
					public int compare(Sample o1, Sample o2) {
						return o2.getQuantity()-o1.getQuantity();
					}
					
				});
				
				for(Sample obj:itemsArray){
					System.out.println(obj.getItem()+" "+obj.getQuantity());
				}
				temp_list.add(itemsArray);
				next.handle(Future.succeededFuture());
				
				
			}else{
				fut.fail(res.cause());
				System.out.println("query failed");
				System.out.println(res.cause());
			}
		});

	}

	private void updateDatabase_fromKiosk(Handler<AsyncResult<Void>> next, int cardID,
			double balance, Future<Void> fut_a) {
		
		String q1="insert into Balance_reg(id,balance) values("+cardID+","+balance+")";

		this.conn.execute(q1,res2->{
			if(res2.succeeded()){
			//	System.out.println("inserted cardID into pos db succesfully");
				next.handle(Future.succeededFuture());
				return;
			}else{
				System.out.println("inserted cardID into server db failed");
				fut_a.fail(res2.cause());
				return;
			}
		});

	}


	private void updateMyRegister(Handler<AsyncResult<Void>> next, int cardNum,
			String location, double amount,
			String date,String itemName ,int quantity,Future<Void> fut_a) {

		String query="SELECT * from CBR WHERE id="+cardNum+" order by transNum asc";
		this.conn.query(query,sel->{
			if(sel.succeeded()){
				//System.out.println("select query successfull");
				
				int numRows=sel.result().getNumRows();
				Future<Void> fut_my=Future.future();

				MyCard newest_temp=new MyCard(cardNum,amount,new MyTransaction(location,amount,date,itemName,quantity));
				
				if(numRows==0){
					//System.out.println("numRows=0");
					updateDB(res->{
						if(res.succeeded()){
							//System.out.println("success in numrows=0");
							next.handle(Future.succeededFuture());
						}
						else
							System.out.println("fail in numrows=0");
					},newest_temp,fut_my,false,-1);
					
					
				}else{
					numRows=sel.result().getNumRows();
					JsonObject a=sel.result().getRows().get(numRows-1); //last row
					int lastTransNum=(int)a.getValue("transNum");
					//System.out.println(portNum+" "+cardNum+"////////////////////////////////////////////////////////"+lastTransNum);
					updateDB(res->{
						if(res.succeeded()){
							//System.out.println("success in numrows=0");
							next.handle(Future.succeededFuture());
						}
						else
							System.out.println("fail in numrows=0");
					},newest_temp,fut_my,true,lastTransNum+1);
					
				}

			}else{
				System.out.println("failed select");
				fut_a.failed();
			}
		});
		
	}

	
	public void getStatus(Handler<AsyncResult<Object>> next, Future<Void> myFut){
		temp_list=new ArrayList<Object>();
		
		System.out.println("--------------SERVER OUTPUT-----------");
		Future<Void> fut=Future.future();
		getTopSellingItems(q1->getTopSellingPOS(q2->{
			if(q2.succeeded()){
				//System.out.println("leave getStatus");
				next.handle(Future.succeededFuture(temp_list));
			}else{
				myFut.fail(q2.cause());
			}
		},fut),fut);
							
	}
	
	protected void getTopSellingPOS(Handler<AsyncResult<Object>> next, Future<Void> fut) {
		temp_list=new ArrayList<Object>();
		
		String query="select location,sum(quantity*amount) from CBR group by location";
		this.conn.query(query,res->{
			if(res.succeeded()){
				//System.out.println("QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ");
				//System.out.println("select2");
				int numItems=res.result().getNumRows();
				Sample_pos itemsArray[]=new Sample_pos[numItems];
				int index=0;
				
				for(JsonObject obj:res.result().getRows()){
					itemsArray[index++]=new Sample_pos((String)obj.getValue("location"),(double)obj.getValue("sum(quantity*amount)"));	
				}
				
				Arrays.sort(itemsArray,new Comparator<Sample_pos>(){

					@Override
					public int compare(Sample_pos o1, Sample_pos o2) {
						if(o1.getCost()<o2.getCost())
							return 1;
						return -1;
					}
					
				});
				
				for(Sample_pos obj:itemsArray){
					System.out.println(obj.getItem()+" "+obj.getCost());
				}
				temp_list.add(itemsArray);
				next.handle(Future.succeededFuture());
				
			}else{
				fut.fail(res.cause());
				System.out.println("query failed");
				System.out.println(res.cause());
			}
		});

		
	}

	public void updateDB(Handler<AsyncResult<Void>> next,MyCard c ,Future<Void> fut,boolean check,int transNum){
		//System.out.println("in updateDB_initial");

		int lNum=check?transNum:1;
		
		String data=c.getCardNum()+","+lNum+",'"+c.getLastTransaction().getLocation()+"',"+
				c.getLastTransaction().getAmount()+",'"+c.getLastTransaction().getDate()+"','"+c.getLastTransaction().getItemName()
				+"',"+c.getLastTransaction().getQuantity();

		String query="INSERT INTO CBR (id,transNum,location,amount,timeOfTransaction,itemName,quantity) "
				+ "VALUES ("+data+")";

		//System.out.println(portNum);
		//System.out.println(query);

		this.conn.execute(query,ar->{
			if(ar.failed()){
				System.out.println("insert_CBR fail in pos");

				fut.fail(ar.cause());
				return;
			}else{
				//System.out.println("insert_CBR succesfully");

				String findBalance="select balance from Balance_reg where id = "+c.getCardNum();
				this.conn.query(findBalance,rp->{
					if(rp.succeeded()){
						if(rp.result().getNumRows()==0){
							fut.fail(rp.cause());
							return;
						}
							
						
						JsonObject b=rp.result().getRows().get(0); 
						Double presentBalance=(Double)(b.getValue("balance"));
						double newBalance=presentBalance-(c.getLastTransaction().getAmount()*c.getLastTransaction().getQuantity());

						String q1="update Balance_reg set balance = "+newBalance+" where id = "+c.getCardNum();

						this.conn.execute(q1,res2->{
							if(res2.succeeded()){
								//System.out.println("updated_balance succesful ");
								temp=new MyCard(c.getCardNum(),newBalance,new MyTransaction(c.getLastTransaction().getLocation()
										,c.getLastTransaction().getAmount(),c.getLastTransaction().getDate(),
										c.getLastTransaction().getItemName(),c.getLastTransaction().getQuantity()));
								next.handle(Future.succeededFuture());
								return;
							}else{
								System.out.println("failed");
								fut.fail(res2.cause());
								return;
							}
						});

					}else{
						System.out.println("select failed");
						fut.fail(rp.cause());
					}
				});

			}
		});
	}

	public void startProcess(Handler<AsyncResult<Void>> next, Future<Void> fut1) throws Exception {

		Future<Void> fut=Future.future();
		establishConnection((connection)-> initializeDB(connection,init->{
			createServer(last->{
				if(last.succeeded()){
					System.out.println("finished creating server");
					next.handle(Future.succeededFuture());
				}
				else{
					fut1.fail(last.cause());
					System.out.println(last.cause());
				}
			},fut);
		},fut),fut);


	}
	
	public String parseItems(List<Object> list){
		Sample[] itemsArray=(Sample[])list.get(0);
		StringBuilder sb=new StringBuilder("");
		for(Sample obj:itemsArray){
			sb.append(obj.getItem()+"="+obj.getQuantity()+"\n");
		}
		return sb.toString();
		
	}
	
	public String parseItems_pos(List<Object> list){
		Sample_pos[] itemsArray=(Sample_pos[])list.get(0);
		StringBuilder sb=new StringBuilder("");
		for(Sample_pos obj:itemsArray){
			sb.append(obj.getItem()+"="+obj.getCost()+"\n");
		}
		return sb.toString();
		
	}
	
}
