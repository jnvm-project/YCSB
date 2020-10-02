export JAVA_HOME=/home/anatole/jdk8u/build/linux-x86_64-normal-server-release/jdk
#export JAVA_OPTS="-Xmx100g -XX:+UseG1GC -XX:+UseNUMA -XX:InitiatingHeapOccupancyPercent=0" #-agentlib:hprof=cpu=samples"
#PIN_CPU=""
#PIN_CPU="taskset -c 0-19,80-99"

OUTDIR="./out"
EXPDIR=$OUTDIR/exp00.heapsize.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data
mkdir -p $EXPDIR $LOGDIR $DATADIR

YCSB_DIR=..
ISPN_DFLT_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-config.xml"

bindings="infinispan"
recordcounts="15000000"
minoperationcount=100000000
fieldcounts="10"
fieldlength="100"
workloads="workloadf"
distribution="zipfian"
threads="10"
ycsb_jobs="run"
dataintegrity="true"

loadcacheproportion="10"
cacheproportions="1 10" # 100"
defaultreadonly="false"
defaultpreload="true"

for binding in $bindings ; do
  offheap=false
  ISPN_CFG=$ISPN_DFLT_CFG
  for fieldcount in $fieldcounts ; do
  for recordcount in $recordcounts ; do
    [ $recordcount -lt $minoperationcount ] && operationcount=$minoperationcount\
                                            || operationcount=$recordcount
    cachesizes=""
    for cacheproportion in $cacheproportions ; do
      cachesizes=$cachesizes" "$(( $recordcount * $cacheproportion / 100 ))
    done
    loadcachesize=$(( $recordcount * $loadcacheproportion / 100 ))

    #rm -fr /pmem{0,1,2,3}/*
    #export JAVA_OPTS="-Xmx25g -XX:+UseG1GC"
    #PIN_CPU="taskset -c 0-19,80-99"
    #sed -e 's/size=\".*\"/size=\"'"${loadcachesize}"'\"/g' -i $ISPN_CFG
    #sed -e 's/read-only=\"true\"/read-only=\"'"${defaultreadonly}"'\"/g' \
    #    -e 's/read-only=\"false\"/read-only=\"'"${defaultreadonly}"'\"/g' -i $ISPN_CFG
    #$PIN_CPU ${YCSB_DIR}/bin/ycsb.sh load $binding -P ${YCSB_DIR}/workloads/workloadf -threads 10\
    #  -p dataintegrity=true\
    #  -p offheap=$offheap\
    #  -p recordcount=$recordcount\
    #  -p operationcount=$operationcount\
    #  -p fieldcount=$fieldcount\
    #  -p requestdistribution=$distribution\
    #  -p measurementtype=hdrhistogram\
    #  -p hdrhistogram.output.path=$LOGDIR/$binding.load.workloadf."true".$recordcount.$loadcachesize.$fieldcount.$distribution.10.hdr.log\
    #  >> $LOGDIR/$binding.load.workloadf."true".$recordcount.$loadcachesize.$fieldcount.$distribution.10.log
    for cachesize in $cachesizes ; do
      #[ $cachesize -eq 1500000 ] && PIN_CPU="taskset -c 0-19,80-99"
      #[ $cachesize -eq 15000000 ] && PIN_CPU=""
      [ $cachesize -eq 150000 ] && export JAVA_OPTS="-Xmx30g -XX:+UseG1GC" && PIN_CPU="taskset -c 0-19,80-99" #15M entries
      [ $cachesize -eq 1500000 ] && export JAVA_OPTS="-Xmx40g -XX:+UseG1GC" && PIN_CPU="taskset -c 0-19,80-99" #15M entries
      [ $cachesize -eq 15000000 ] && export JAVA_OPTS="-Xmx100g -XX:+UseG1GC -XX:+UseNUMA" && PIN_CPU="" #15M entries
      #[ $cachesize -eq 1500000 ] && export JAVA_OPTS="-Xmx25g -XX:+UseG1GC" && PIN_CPU="taskset -c 0-19,80-99" #10M entries
      #readonly="true"
      #readonly=$defaultreadonly
      #preload="false"
      #preload=$defaultpreload
      #[ $(( $cachesize / $recordcount )) -eq 1 ] && readonly="true" || readonly=$defaultreadonly
    for readonly in "true" "false" ; do
    for preload in "true" "false" ; do
      sed -e 's/size=\".*\"/size=\"'"${cachesize}"'\"/g' -i $ISPN_CFG
      sed -e 's/read-only=\"true\"/read-only=\"'"${readonly}"'\"/g' \
          -e 's/read-only=\"false\"/read-only=\"'"${readonly}"'\"/g' -i $ISPN_CFG
      sed -e 's/preload=\"true\"/preload=\"'"${preload}"'\"/g' \
          -e 's/preload=\"false\"/preload=\"'"${preload}"'\"/g' -i $ISPN_CFG
    for thread in $threads ; do
      for workload in $workloads ; do
        for integrity in $dataintegrity ; do
          for ycsb_job in $ycsb_jobs ; do
            $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh $ycsb_job $binding -P ${YCSB_DIR}/workloads/$workload -threads $thread\
              -p dataintegrity=$integrity\
              -p offheap=$offheap\
              -p recordcount=$recordcount\
              -p operationcount=$operationcount\
              -p fieldcount=$fieldcount\
              -p requestdistribution=$distribution\
              -p measurement.histogram.verbose=true\
              -p measurementtype=hdrhistogram\
              -p hdrhistogram.output.path=$LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$thread.$readonly.$preload.hdr.log\
              >> $LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$thread.$readonly.$preload.log
          done
        done
      done
    done
    done
    done
    done
  done
  done
done
