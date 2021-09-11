numa_node=${NUMA_NODE:-0}
jheap_size=${JHEAP_SIZE:-20g}
export JAVA_HOME=/home/anatole/jdk8u/build/linux-x86_64-normal-server-release/jdk
export JAVA_OPTS="-Xmx${jheap_size} -XX:+UseG1GC" #-agentlib:hprof=cpu=samples"
PIN_CPU="numactl -N $numa_node --"

OUTDIR="./out"
EXPDIR=$OUTDIR/exp0.exectime.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data
mkdir -p $EXPDIR $LOGDIR $DATADIR

YCSB_DIR=..
ISPN_DFLT_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-config.xml"
ISPN_JNVM_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-jnvm-config.xml"
#ISPN_JPFA_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-jpfa-config.xml"
ISPN_AUTO_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-autopersist-config.xml"
ISPN_PCJ_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-pcj-config.xml"

#bindings="infinispan-autopersist infinispan"
bindings="infinispan infinispan-jnvm infinispan-jpfa infinispan-pcj"
#recordcounts="1000000"
recordcounts="3000000"
minoperationcount=10000000
defaultfieldcount="10"
fieldcounts="10"
defaultfieldlength="100"
fieldlengths="100"
workloads="workloada workloadb workloadc workloadd workloadf" #workloade"
distribution="default"
threads="1"
ycsb_jobs="run"
dataintegrity="true"
ycsb_preload="-preload"

n_run=1
#n_run=6

loadcacheproportion="10"
#cacheproportions="100"
cacheproportions="10"
defaultreadonly="false"
defaultpreload="true"

#external parameter overrides
if [ $EXP_PRESET == "tiny" ] ; then
recordcounts="10000"
minoperationcount="10000"
fi

for binding in $bindings ; do
  m="default"
  p="default"
  fs="none"
  if [ $binding == "infinispan-jnvm" ] ; then
    offheap=true
    pcj=false
    ISPN_CFG=$ISPN_JNVM_CFG
  elif [ $binding == "infinispan-jpfa" ] ; then
    offheap=true
    pcj=false
    #ISPN_CFG=$ISPN_JPFA_CFG
    ISPN_CFG=$ISPN_JNVM_CFG
  elif [ $binding == "infinispan-autopersist" ] ; then
    offheap=false
    pcj=false
    ISPN_CFG=$ISPN_AUTO_CFG
  elif [ $binding == "infinispan-pcj" ] ; then
    offheap=false
    pcj=true
    ISPN_CFG=$ISPN_PCJ_CFG
  else
    offheap=false
    pcj=false
    fs="pmem0"
    ISPN_CFG=$ISPN_DFLT_CFG
  fi
  for fieldcount in $fieldcounts ; do
  for fieldlength in $fieldlengths ; do
  for recordcount in $recordcounts ; do
    #[ $binding == "infinispan-jnvm" ] && minoperationcount="3000000"
    [ $recordcount -lt $minoperationcount ] && operationcount=$minoperationcount\
                                            || operationcount=$recordcount
    cachesizes=""
    if [ $binding == "infinispan-jnvm" ] ; then
      cachesizes="1"
      loadcachesize="1"
    elif [ $binding == "infinispan-jpfa" ] ; then
      cachesizes="1"
      loadcachesize="1"
    elif [ $binding == "infinispan-autopersist" ] ; then
      cachesizes="1"
      loadcachesize="1"
#    elif [ $binding == "infinispan-pcj" ] ; then
#      cachesizes="1"
#      loadcachesize="1"
    else
      for cacheproportion in $cacheproportions ; do
        cachesizes=$cachesizes" "$(( $recordcount * $cacheproportion / 100 ))
      done
      loadcachesize=$(( $recordcount * $loadcacheproportion / 100 ))
    fi

    rm -fr /pmem{0,1,2,3}/* /dev/shm/* /blackhole/*
    if [ -z $ycsb_preload ] ; then
    sed -e 's/size=\".*\"/size=\"'"${loadcachesize}"'\"/g' -i $ISPN_CFG
    sed -e 's/read-only=\"true\"/read-only=\"'"${defaultreadonly}"'\"/g' \
        -e 's/read-only=\"false\"/read-only=\"'"${defaultreadonly}"'\"/g' -i $ISPN_CFG
    $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh load $binding -P ${YCSB_DIR}/workloads/workloada -threads 1\
      -p dataintegrity=$dataintegrity\
      -p offheap=$offheap\
      -p pcj=$pcj\
      -p recordcount=$recordcount\
      -p operationcount=$operationcount\
      -p fieldcount=$fieldcount\
      -p fieldlength=$fieldlength\
      -p measurement.trackjvm=true\
      -p measurementtype=hdrhistogram\
      -p hdrhistogram.output.path=$LOGDIR/$binding.load.workloada.$dataintegrity.$recordcount.$loadcachesize.$fieldcount.$distribution.1.hdr.log\
      >> $LOGDIR/$binding.load.workloada.$dataintegrity.$recordcount.$loadcachesize.$fieldcount.$distribution.1.log
    fi
    for cachesize in $cachesizes ; do
      #[ $(( $cachesize / $recordcount )) -eq 1 ] && readonly="true" || readonly=$defaultreadonly
      sed -e 's/size=\".*\"/size=\"'"${cachesize}"'\"/g' -i $ISPN_CFG
      #sed -e 's/read-only=\"true\"/read-only=\"'"${readonly}"'\"/g' \
      #    -e 's/read-only=\"false\"/read-only=\"'"${readonly}"'\"/g' -i $ISPN_CFG
      readonly=$defaultreadonly
      preload=$defaultpreload
    for thread in $threads ; do
      for workload in $workloads ; do
        for integrity in $dataintegrity ; do
          for ycsb_job in $ycsb_jobs ; do
          for i in `seq 1 $n_run` ; do
            [ ! -z $ycsb_preload ] && rm -fr /pmem{0,1,2,3}/* /dev/shm/* /blackhole/*
            $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh $ycsb_job $binding -P ${YCSB_DIR}/workloads/$workload $ycsb_preload -s -threads $thread\
              -p dataintegrity=$integrity\
              -p offheap=$offheap\
              -p pcj=$pcj\
              -p recordcount=$recordcount\
              -p operationcount=$operationcount\
              -p fieldcount=$fieldcount\
              -p fieldlength=$fieldlength\
              -p measurement.trackjvm=true\
              -p measurementtype=hdrhistogram\
              -p hdrhistogram.output.path=$LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$fieldlength.$distribution.$thread.$readonly.$preload.$m.$p.$fs.r$i.hdr.log\
              >> $LOGDIR/$binding.$ycsb_job.$workload.$integrity.$recordcount.$cachesize.$fieldcount.$fieldlength.$distribution.$thread.$readonly.$preload.$m.$p.$fs.r$i.log
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
