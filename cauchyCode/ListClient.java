import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;







/*
 * Class which is responsible for testing...
 * it updates, calculates the time taken for that update, nodes added, and time taken to recover
 * whenever recovery needs to be performed an instance of this class shud be created
 * It will talk to the original and the backups, get the existing data and recover the faulty ones
 * as of now I do not allow faults in the backup but that is not a big deal..it can easily be extended
 */

public class ListClient{

	int numPrimaries;
	int numFaults;
	Fusion fusionObject;
	PrintStream[] pout;
	ObjectOutputStream[] objOut;
	ObjectInputStream[] objIn; 
	LinkedList<Integer>[] testLists; 

    public ListClient(int numPrimaries, int numFaults){
		this.numPrimaries = numPrimaries;
		this.numFaults = numFaults;
		Fusion.initialize(numPrimaries, numFaults);
		testLists = new LinkedList[numPrimaries]; 
		for(int i =0; i < numPrimaries; ++i){
			testLists[i] = new LinkedList<Integer>(); 
		}
		pout = new PrintStream[numPrimaries+numFaults];
		objOut = new ObjectOutputStream[numPrimaries+numFaults];
		objIn = new ObjectInputStream[numPrimaries+numFaults];
		try {
			//handle to all the original data structures
			for(int i =0; i < numPrimaries ; ++i){
			    Socket s = new Socket(Symbols.nameServer, 
			            Symbols.DataPort+i);
				pout[i] = new PrintStream(s.getOutputStream());
				objOut[i] = new ObjectOutputStream(s.getOutputStream());
				objIn[i] = new ObjectInputStream(s.getInputStream());

				if(Util.debugFlag)System.out.println("Connected to Data Server "+ i);
			}
			
			//handle to all the backup structures
			for(int i =0 ; i < numFaults; ++i){
			    Socket s = new Socket(Symbols.nameServer, 
			            Symbols.BackupPort+i);
				pout[i+numPrimaries] = new PrintStream(s.getOutputStream());
				objOut[i+numPrimaries] = new ObjectOutputStream(s.getOutputStream());
				objIn[i+numPrimaries] = new ObjectInputStream(s.getInputStream());

				if(Util.debugFlag)System.out.println("Connected to Backup Server "+ i);
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
 
	}
    
    
    public long recover(Vector<Integer> erasures){
    	long recTime =0; 
		int[] erasuresArray = new int[numFaults+1];
		for(int i =0; i < erasures.size();++i){
			erasuresArray[i] = erasures.get(i);
		}

		Vector< Vector<Integer> > primaries = getPrimaries(erasures);
		if(Util.debugFlag)System.out.println(primaries);
    	Vector< Vector<int[]> > backups =  getBackups();//we assume failures only in the primaries
    	if(Util.debugFlag)System.out.println(backups);
    	Vector<Vector<Integer>> indexInfo = getIndexInfo();
    	if(Util.debugFlag)System.out.println(indexInfo);
		

		long startTime = Util.getCurrentTime();		
		for(int fusedNodeNumber =0; fusedNodeNumber < indexInfo.size();++fusedNodeNumber){
			int[] code	 = new int[numFaults*Fusion.getW()];
			for(int i=0; i < numFaults*Fusion.getW();++i){
				code[i]=0; 
			}
			
			int[] data = new int[numPrimaries*Fusion.getW()];
			for(int i=0; i < numPrimaries*Fusion.getW();++i){
				data[i]=0; 
			}
			for(int backupId =0; backupId < numFaults; ++backupId){
				for(int i=0; i < Fusion.getW();++i){
					code[backupId*Fusion.getW()+i]=backups.get(backupId).get(fusedNodeNumber)[i];
				}
			}
			for(int k =0; k < numPrimaries; ++k){
				int indexOfPrimary = indexInfo.get(fusedNodeNumber).get(k);  
				if(!erasures.contains(k) && (indexOfPrimary != -1)){
					Vector<Integer> original =  primaries.get(k);
					data[k*Fusion.getW()]=original.get(indexOfPrimary);
				}
			}	
			try {
				if(Util.debugFlag){
					System.out.println("-------------------------");
					System.out.print("data:");
					for(int p=0; p < numPrimaries*Fusion.getW();++p){
						System.out.print(data[p]);
					}
					System.out.println();
					
					System.out.print("code:");
					for(int p=0; p < numFaults*Fusion.getW();++p){
						System.out.print(code[p]);
					}
					System.out.println();
				}
				data = Fusion.getRecoveredData(code,data,erasuresArray);
			} catch (InterfaceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			for(int k =0; k < numPrimaries; ++k){
				int indexOfPrimary = indexInfo.get(fusedNodeNumber).get(k);
				if(erasures.contains(k) && (indexOfPrimary != -1)){
					Vector<Integer> original =  primaries.get(k);
					if(indexOfPrimary >= original.size()){
						for(int m =original.size(); m <= indexOfPrimary;++m){
							original.add(-1); 
						}
					}
					original.set(indexOfPrimary,data[k*Fusion.getW()]);
				}
			}
			
		}
		
		
		long endTime = Util.getCurrentTime();
		recTime = recTime + (endTime - startTime); 

		if(Util.debugFlag)System.out.print("Recovered Originals:");
		if(Util.debugFlag)System.out.println(primaries);
		for(int i = 0; i < numPrimaries ; ++i){
			Vector<Integer> original =  primaries.get(i);
			LinkedList test = testLists[i];
			for(int j =0; j < test.size(); ++j){
				int recovered = original.get(j);
				int actual = (Integer)test.get(j); 
				try {
					if(recovered != actual)
						throw new Exception("Recovery Error");
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(0); 
				}
			}
		}
		
		return (recTime);
		

    }

    public Vector<Vector<Integer>> getPrimaries(Vector<Integer> erasures){
    	Vector<Vector<Integer>> primaries = new Vector<Vector<Integer>>(); 
    	for(int i =0; i < numPrimaries ;++i){
    		if(!erasures.contains(i)){
    			try {
        			pout[i].println("req");
        			pout[i].flush();
    				Vector<Integer> data = (Vector<Integer>)objIn[i].readObject();
					primaries.add(data);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    		else{//for all the faulty ones...just fill in dummy data
    			Vector<Integer> dummy = new Vector<Integer>();
    			primaries.add(dummy);
    		}
    	}
    	return primaries; 
    }

    public Vector<Vector<int[]>> getBackups(){
    	Vector<Vector<int[]>> backups = new Vector<Vector<int[]>>(); 
    	
    	for(int i = numPrimaries; i < numPrimaries+numFaults ;++i){
			try {
    			pout[i].println("reqData");
    			pout[i].flush();
				Vector<int[]> data = (Vector<int[]>)objIn[i].readObject();
				backups.add(data);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	return backups; 
    }

    public Vector<Vector<Integer>> getIndexInfo(){
    	Vector<Vector<Integer>> indexInfo = new Vector<Vector<Integer>>(); 
		try {
			pout[numPrimaries ].println("reqIndex");
			pout[numPrimaries].flush();
			indexInfo = (Vector<Vector<Integer>>)objIn[numPrimaries].readObject();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return indexInfo;    	
    }

    //performs updates, sends a request to all the backups to give it back the time taken for it's set of updates
    //then calculates the max of it all to give the time for updates
    public long calcUpdateTime(int numberOfOperations){
     	complexLinkedlistTests(numberOfOperations);
    	//simpleLinkedlistTests();
    	int backupId = numPrimaries;
    	long updateTime = 0;
    	
		try {
			pout[backupId].println("time");
			pout[backupId].flush();
			System.out.println("done");
			updateTime = (Long)objIn[backupId].readObject();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return updateTime; 
    }

    
    //kills the first numFaults number of servers and finds the time taken for the operation
    public long calcrecoveryTime(){
    	Vector<Integer> erasures = new Vector<Integer>(numFaults+1);
    	//for the purpose of testing just always kill the first numFaults number of data servers
    	for(int i =0; i < numFaults; ++i){
    		erasures.add(i);
    	}
    	//just to put the -1 in
    	erasures.add(-1);
		long recoveryTime = recover(erasures);
		return recoveryTime; 
    }
    
    
    public int calcSize(){
    	int totalSizeBackups=0;
    	try{
	    	//Each backup has the same size...so just get from one and multiply
	 		pout[numPrimaries].println("size");
	    	pout[numPrimaries].flush();
			int  sizeOfOneBackup  = (Integer)objIn[numPrimaries].readObject();
			
			totalSizeBackups = numFaults*sizeOfOneBackup;
    	} catch (IOException e) {
		// TODO Auto-generated catch block
    		e.printStackTrace();
    	} catch (ClassNotFoundException e) {
		// TODO Auto-generated catch block
    		e.printStackTrace();
    	}
    	return totalSizeBackups; 
    }

    void listAdd(int listNum, int index, int element){
    	testLists[listNum].add(index, element); 
		String msgForOriginals = "add"+" "+index+" "+element;
		if(Util.debugFlag)System.out.println(msgForOriginals+" "+listNum);
		pout[listNum].println(msgForOriginals);
		pout[listNum].flush();
	}
	
	void listRemove(int listNum, int index){
    	testLists[listNum].remove(index);
		String msgForOriginals = "remove"+" "+index;
		if(Util.debugFlag)System.out.println(msgForOriginals+" "+listNum);
		pout[listNum].println(msgForOriginals);
		pout[listNum].flush();
	}
	
    
    //complex final linked list tests
    public void complexLinkedlistTests(int numberOfUpdates){
		int listId = 0;
		int noOfadds =0;
		int noOfdeletes=0;

		int[] inserts = new int[numPrimaries];
		int[] deletes = new int[numPrimaries];
		
		Random random = new Random(5);
		for (int i = 0; i < numberOfUpdates; i += 1) {
			// q = random.nextInt(numPrimaries);
			listId = (listId + 1) % numPrimaries;
			int size = inserts[listId] - deletes[listId];
			double operation = random.nextDouble();
			int location;
			if (operation < Util.threshold) {
				if (size == 0) {
					i = i - 1;
					continue;
				}
				location = random.nextInt(size);
				listRemove(listId, location);
				++deletes[listId];
				noOfdeletes++;
			} else {
				location = random.nextInt(size + 1);
				listAdd(listId, location, i);
				++inserts[listId];
				noOfadds++;
			}
		}
    }
    
    
    public void simpleLinkedlistTests(){
    	//linked list tests
   	
		listAdd(0, 0, 1);
		listAdd(1, 0, 2);
		listAdd(2, 0, 3);
		listRemove(1,0);

   	
		listAdd(0,0,4);
		listRemove(0,0);
		listAdd(0,1,5);
		listAdd(0,2,6);
		listAdd(0,1,7);
		listRemove(0,3);

		listAdd(1,0,8);
		listAdd(1,1,9);
		moreTests();
    }
    
    public void moreTests(){
		listAdd(0,0,11);
		listAdd(0,1,12);
		listAdd(0,2,13);

		listAdd(1,0,24);
		listAdd(1,1,25);
		listAdd(1,2,26);

		listAdd(2,0,37);
		listAdd(2,1,38);
		listAdd(2,2,39);
		
		listRemove(2,1);listRemove(2,1);
		
		listAdd(0,1,14);
		
		listRemove(1,1);
    }
    
    public static void main(String[] args) {
    	int numPrimaries = Integer.parseInt(args[0]);
    	int numFaults = Integer.parseInt(args[1]);
    	int numberOfUpdates =Integer.parseInt(args[2]);

    	ListClient orc = new ListClient(numPrimaries,numFaults);
    	//performs updates and based on that calculates update time

    	long updateTime= orc.calcUpdateTime(numberOfUpdates);
    	
    	//recovers the state of the system w.r.t to the updates peformed above
    	try {
    		Thread.sleep(60000L);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		long recoveryTime = orc.calcrecoveryTime();
    	//compares the total size of the primaries vis a vis the backups
    	int size = orc.calcSize();
    	
    	System.out.println(numPrimaries+","+numFaults+","+numberOfUpdates+","+size+","+updateTime+","+recoveryTime+","+Util.enCodingTime+","+Util.deCodingTime);
    }

}
