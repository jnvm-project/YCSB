numa_node=${NUMA_NODE:-0}
export JAVA_HOME=/home/anatole/jdk8u/build/linux-x86_64-normal-server-release/jdk
export JAVA_OPTS="-Xmx20g -XX:+UseG1GC" #-agentlib:hprof=cpu=samples"
PIN_CPU="numactl -N $numa_node --"

OUTDIR="./out"
EXPDIR=$OUTDIR/exp2.keycount.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data
mkdir -p $EXPDIR $LOGDIR $DATADIR

YCSB_DIR=..
ISPN_DFLT_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-config.xml"
ISPN_JNVM_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-jnvm-config.xml"

bindings="infinispan infinispan-jnvm"
#bindings="jnvm-vmap jnvm-rmap infinispan"
recordcounts="10000 100000 1000000 5000000 10000000"
minoperationcount=3000000
fieldcounts="10"
workloads="workloada workloadb workloadc"
distribution="zipfian"
threads=1
ycsb_jobs="run"
dataintegrity="true"

loadcacheproportion="10"
cacheproportions="10"

for binding in $bindings ; do
  if [ $binding == "infinispan-jnvm" ] ; then
    offheap=true
    ISPN_CFG=$ISPN_JNVM_CFG
  else
    offheap=false
    ISPN_CFG=$ISPN_DFLT_CFG
  fi
  for fieldcount in $fieldcounts ; do
  for recordcount in $recordcounts ; do
    [ $recordcount -lt $minoperationcount ] && operationcount=$minoperationcount\
                                            || operationcount=$recordcount
    cachesizes=""
    if [ $binding == "infinispan-jnvm" ] ; then
      cachesizes="1"
      loadcachesize="1"
    else
      for cacheproportion in $cacheproportions ; do
        cachesizes=$cachesizes" "$(( $recordcount * $cacheproportion / 100 ))
      done
      loadcachesize=$(( $recordcount * $loadcacheproportion / 100 ))
    fi

    rm -fr /pmem{0,1,2,3}/*
    sed -e 's/size=\".*\"/size=\"'"${loadcachesize}"'\"/g' -i $ISPN_CFG
    $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh load $binding -P ${YCSB_DIR}/workloads/workloada -threads $threads\
      -p dataintegrity=true\
      -p offheap=$offheap\
      -p recordcount=$recordcount\
      -p operationcount=$operationcount\
      -p fieldcount=$fieldcount\
      -p requestdistribution=$distribution\
      -p measurementtype=hdrhistogram\
      -p hdrhistogram.output.path=$LOGDIR/$binding.load.workloada."true".$recordcount.$loadcachesize.$fieldcount.$distribution.$threads.hdr.log\
      >> $LOGDIR/$binding.load.workloada."true".$recordcount.$loadcachesize.$fieldcount.$distribution.$threads.log
    for cachesize in $cachesizes ; do
      sed -e 's/size=\".*\"/size=\"'"${cachesize}"'\"/g' -i $ISPN_CFG
      for workload in $workloads ; do
        for integrity in $dataintegrity ; do
          for ycsb_job in $ycsb_jobs ; do
            $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh $ycsb_job $binding -P ${YCSB_DIR}/workloads/$workload -threads $threads\
              -p dataintegrity=$integrity\
              -p offheap=$offheap\
              -p recordcount=$recordcount\
              -p operationcount=$operationcount\
              -p fieldcount=$fieldcount\
              -p requestdistribution=$distribution\
              -p measurementtype=hdrhistogram\
              -p hdrhistogram.output.path=$LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$threads.hdr.log\
              >> $LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$threads.log
          done
        done
      done
    done
  done
  done
done
