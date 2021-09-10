numa_node=${NUMA_NODE:-0}
export JAVA_HOME=/home/anatole/jdk8u/build/linux-x86_64-normal-server-release/jdk
#export JAVA_OPTS="-Xmx100g -XX:+UseG1GC -XX:+UseNUMA -XX:InitiatingHeapOccupancyPercent=0" #-agentlib:hprof=cpu=samples"
#PIN_CPU=""
#PIN_CPU="taskset -c 0-19,80-99"
#-XX:-UseCompressedOops -XX:+UseNUMR
#numactl -i all -N 0 --
#numactl -p 0 -N 0 --

OUTDIR="./out"
EXPDIR=$OUTDIR/exp00.heapsize.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data
mkdir -p $EXPDIR $LOGDIR $DATADIR

YCSB_DIR=..
ISPN_DFLT_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-config.xml"

bindings="infinispan"
recordcounts="15000000"
#recordcounts="14000000"
#minoperationcount=30000000 #singleThread
minoperationcount=100000000
fieldcounts="10"
fieldlength="100"
workloads="workloadf"
distribution="zipfian"
threads="10"
#threads="1" #singleThread
ycsb_jobs="run"
dataintegrity="true" #false"

memory="default preferred interleaved numa"
#oops="default compressed expended"
oops="default expended"

n_run=1
#n_run=6
loadcacheproportion="1"
cacheproportions="1 10 100"
defaultreadonly="false"
defaultpreload="true"

for binding in $bindings ; do
  offheap=false
  ISPN_CFG=$ISPN_DFLT_CFG
  for fieldcount in $fieldcounts ; do
  for recordcount in $recordcounts ; do
    [ $recordcount -lt $minoperationcount ] && operationcount=$minoperationcount\
                                            || operationcount=$recordcount
    #cachesizes="1" #No cache for testing
    cachesizes=""
    for cacheproportion in $cacheproportions ; do
      cachesizes=$cachesizes" "$(( $recordcount * $cacheproportion / 100 ))
    done
    loadcachesize=$(( $recordcount * $loadcacheproportion / 100 ))

    #YCSB LOAD, once per record/field count and binding, with default parameters
    rm -fr /pmem{0,1,2,3}/*
    export JAVA_OPTS="-Xmx20g -XX:+UseG1GC"
    PIN_CPU="numactl -p $numa_node -N $numa_node"
    sed -e 's/size=\".*\"/size=\"'"${loadcachesize}"'\"/g' -i $ISPN_CFG
    sed -e 's/read-only=\"true\"/read-only=\"'"${defaultreadonly}"'\"/g' \
        -e 's/read-only=\"false\"/read-only=\"'"${defaultreadonly}"'\"/g' -i $ISPN_CFG
    $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh load $binding -P ${YCSB_DIR}/workloads/workloadf -threads 10\
      -p dataintegrity=true\
      -p offheap=$offheap\
      -p recordcount=$recordcount\
      -p operationcount=$operationcount\
      -p fieldcount=$fieldcount\
      -p requestdistribution=$distribution\
      -p measurementtype=hdrhistogram\
      -p hdrhistogram.output.path=$LOGDIR/$binding.load.workloadf."true".$recordcount.$loadcachesize.$fieldcount.$distribution.10.hdr.log\
      >> $LOGDIR/$binding.load.workloadf."true".$recordcount.$loadcachesize.$fieldcount.$distribution.10.log

    #YCSB RUN parameter loop
    for cachesize in $cachesizes ; do
      #NUMACTL memory affinity
      for m in $memory ; do
        mem=""; numa="";
        case $m in
          default)
            mem=""; numa="";;
          preferred)
            mem="-p 0"; numa="";;
          interleaved)
            mem="-i all"; numa="";;
          numa)
            mem=""; numa="-XX:+UseNUMA";;
          *)
            mem=""; numa="";;
        esac
      #JVM OOP compression
      for p in $oops ; do
        oop="";
        case $p in
          default)
            oop="";;
          compressed)
            oop="-XX:+UseCompressedOops";;
          expended)
            oop="-XX:-UseCompressedOops";;
          *)
            oop="";;
        esac
      [ $cachesize -eq 1 ] && export JAVA_OPTS="-Xmx15g -XX:+UseG1GC $oop" && PIN_CPU="numactl $mem -N $numa_node -- " #15M entries
      [ $cachesize -eq 150000 ] && export JAVA_OPTS="-Xmx20g -XX:+UseG1GC $oop" && PIN_CPU="numactl $mem -N $numa_node -- " #15M entries
      [ $cachesize -eq 1500000 ] && export JAVA_OPTS="-Xmx30g -XX:+UseG1GC $oop" && PIN_CPU="numactl $mem -N $numa_node -- " #15M entries
      [ $cachesize -eq 15000000 ] && export JAVA_OPTS="-Xmx100g -XX:+UseG1GC $numa" && PIN_CPU="numactl $mem -N $numa_node -- " #15M entries
      #[ $cachesize -eq 1500000 ] && export JAVA_OPTS="-Xmx25g -XX:+UseG1GC" && PIN_CPU="taskset -c 0-19,80-99" #10M entries
      #readonly="true"
      readonly=$defaultreadonly
      #preload="false"
      preload=$defaultpreload
      #[ $(( $cachesize / $recordcount )) -eq 1 ] && readonly="true" || readonly=$defaultreadonly
    #for readonly in "true" "false" ; do
    #for preload in "true" "false" ; do
      sed -e 's/size=\".*\"/size=\"'"${cachesize}"'\"/g' -i $ISPN_CFG
      sed -e 's/read-only=\"true\"/read-only=\"'"${readonly}"'\"/g' \
          -e 's/read-only=\"false\"/read-only=\"'"${readonly}"'\"/g' -i $ISPN_CFG
      sed -e 's/preload=\"true\"/preload=\"'"${preload}"'\"/g' \
          -e 's/preload=\"false\"/preload=\"'"${preload}"'\"/g' -i $ISPN_CFG
    for thread in $threads ; do
      for workload in $workloads ; do
        for integrity in $dataintegrity ; do
          for ycsb_job in $ycsb_jobs ; do
          for i in `seq 1 $n_run` ; do
            $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh $ycsb_job $binding -P ${YCSB_DIR}/workloads/$workload -threads $thread\
              -p dataintegrity=$integrity\
              -p offheap=$offheap\
              -p recordcount=$recordcount\
              -p operationcount=$operationcount\
              -p fieldcount=$fieldcount\
              -p requestdistribution=$distribution\
              -p measurement.histogram.verbose=true\
              -p measurementtype=hdrhistogram\
              -p hdrhistogram.output.path=$LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$thread.$readonly.$preload.$m.$p.r$i.hdr.log\
              >> $LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$thread.$readonly.$preload.$m.$p.r$i.log
          done
          done
        done
      done
    #done
    #done
    done
    done
    done
  done
  done
done
