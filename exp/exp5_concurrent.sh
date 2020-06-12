export JAVA_HOME=/home/anatole/jdk8u/build/linux-x86_64-normal-server-release/jdk
export JAVA_OPTS="-Xmx20g -XX:+UseG1GC" #-agentlib:hprof=cpu=samples"
PIN_CPU="taskset -c 0-19,80-99"

OUTDIR="./out"
EXPDIR=$OUTDIR/exp5.concurrent.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data
mkdir -p $EXPDIR $LOGDIR $DATADIR

YCSB_DIR=..
ISPN_DFLT_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-config.xml"
ISPN_JNVM_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-jnvm-config.xml"

bindings="infinispan infinispan-jnvm"
#bindings="jnvm-vmap jnvm-rmap infinispan"
recordcounts="1000000"
minoperationcount=10000000
fieldcounts="10"
workloads="insertw workloada updatew workloadc"
distribution="zipfian"
threads="1 "`seq 4 4 40`
ycsb_jobs="run"
dataintegrity="true"

loadcacheproportion="10"
cacheproportions="10 100"
defaultreadonly="false"

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
    [ $binding == "infinispan-jnvm" ] && minoperationcount="30000000"
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
    sed -e 's/read-only=\"true\"/read-only=\"'"${defaultreadonly}"'\"/g' \
        -e 's/read-only=\"false\"/read-only=\"'"${defaultreadonly}"'\"/g' -i $ISPN_CFG
    $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh load $binding -P ${YCSB_DIR}/workloads/workloada -threads 1\
      -p dataintegrity=true\
      -p offheap=$offheap\
      -p recordcount=$recordcount\
      -p operationcount=$operationcount\
      -p fieldcount=$fieldcount\
      -p requestdistribution=$distribution\
      -p measurementtype=hdrhistogram
      -p measurementtype=hdrhistogram\
      -p hdrhistogram.output.path=$LOGDIR/$binding.load.workloada."true".$recordcount.$loadcachesize.$fieldcount.$distribution.1.hdr.log\
      >> $LOGDIR/$binding.load.workloada."true".$recordcount.$loadcachesize.$fieldcount.$distribution.1.log
    for cachesize in $cachesizes ; do
      [ $(( $cachesize / $recordcount )) -eq 1 ] && readonly="true" || readonly=$defaultreadonly
      sed -e 's/size=\".*\"/size=\"'"${cachesize}"'\"/g' -i $ISPN_CFG
      sed -e 's/read-only=\"true\"/read-only=\"'"${readonly}"'\"/g' \
          -e 's/read-only=\"false\"/read-only=\"'"${readonly}"'\"/g' -i $ISPN_CFG
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
              -p measurementtype=hdrhistogram\
              -p hdrhistogram.output.path=$LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$thread.hdr.log\
              >> $LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$distribution.$thread.log
          done
        done
      done
    done
    done
  done
  done
done
