#Script for running tests...

#first kill the previous java processes which are still alive
numStructs=$1
for ((  numFaults = 1 ;  numFaults <= $numStructs;  numFaults++  ))
do
   pkill -9 java
   echo "---------------------"
   echo "Faults = $numFaults"

   for ((  i = 0 ;  i <= ($numFaults \* $numStructs);  i++  ))
   do
     java ReplicatedLinkedList $i &
   done

   for ((  i = 0 ;  i < $numStructs;  i++  ))
   do
     java OriginalLinkedList $i &
   done
 
   sleep 60
   java ReplicationLinkedListOracle  $numStructs $numFaults 
done


