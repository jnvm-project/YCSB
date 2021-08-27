export JAVA_HOME=/home/anatole/jdk8u/build/linux-x86_64-normal-server-release/jdk
export JAVA_OPTS="-Xmx20g -XX:+UseG1GC" #-agentlib:hprof=cpu=samples"
PIN_CPU="numactl -N 0 --"
#PIN_CPU="taskset -c 0-19,80-99"

OUTDIR="./out"
EXPDIR=$OUTDIR/exp5.concurrent.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data
mkdir -p $EXPDIR $LOGDIR $DATADIR

YCSB_DIR=..
ISPN_DFLT_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-config.xml"
ISPN_JNVM_CFG="${YCSB_DIR}/infinispan/src/main/conf/infinispan-jnvm-config.xml"

bindings="infinispan"
#bindings="infinispan infinispan-jnvm"
recordcounts="1000000"
minoperationcount=6000000
maxoperationcount=60000000
defaultfieldcount="10"
fieldcounts="10"
defaultfieldlength="100"
fieldlengths="100"
workloads="workloada workloadc"
#workloads="workloada updatew workloadc insertw"
distribution="zipfian"
threads="1 2 "`seq 4 4 20`
#threads="1 2 "`seq 4 4 40`
ycsb_jobs="run"
dataintegrity="true"
ycsb_preload="-preload"

n_run=1
#n_run=6
m="default"
p="default"
fs="volatile"
#fs="none"

loadcacheproportion="10"
cacheproportions="100"
#cacheproportions="10 100"
defaultreadonly="false"
defaultpreload="true"

for binding in $bindings ; do
  if [ $binding == "infinispan-jnvm" ] ; then
    offheap=true
    pcj=false
    ISPN_CFG=$ISPN_JNVM_CFG
  else
    offheap=false
    pcj=false
    ISPN_CFG=$ISPN_DFLT_CFG
  fi
  for fieldcount in $fieldcounts ; do
  for fieldlength in $fieldlengths ; do
  for recordcount in $recordcounts ; do
    #[ $binding == "infinispan-jnvm" ] && minoperationcount="30000000"
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

    rm -fr /pmem{0,1,2,3}/* /dev/shm/* /blackhole/*
    if [ -z $ycsb_preload ] ; then
    sed -e 's/size=\".*\"/size=\"'"${loadcachesize}"'\"/g' -i $ISPN_CFG
    sed -e 's/read-only=\"true\"/read-only=\"'"${defaultreadonly}"'\"/g' \
        -e 's/read-only=\"false\"/read-only=\"'"${defaultreadonly}"'\"/g' -i $ISPN_CFG
    $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh load $binding -P ${YCSB_DIR}/workloads/workloada -threads 1\
      -p dataintegrity=true\
      -p offheap=$offheap\
      -p pcj=$pcj\
      -p recordcount=$recordcount\
      -p operationcount=$operationcount\
      -p fieldcount=$fieldcount\
      -p fieldlength=$fieldlength\
      -p requestdistribution=$distribution\
      -p measurement.trackjvm=true\
      -p measurementtype=hdrhistogram\
      -p hdrhistogram.output.path=$LOGDIR/$binding.load.workloada."true".$recordcount.$loadcachesize.$fieldcount.$distribution.1.hdr.log\
      >> $LOGDIR/$binding.load.workloada."true".$recordcount.$loadcachesize.$fieldcount.$distribution.1.log
    fi
    for thread in $threads ; do
      operationcount=$(( $minoperationcount * $thread ))
      [ $operationcount -gt $maxoperationcount ] && operationcount=$maxoperationcount
    for workload in $workloads ; do
      #[ $workload == "insertw" ] && operationcount=$recordcount && rm -fr /pmem{0,1,2,3}/*
      #[ $workload != "insertw" ] && operationcount=$minoperationcount
    for cachesize in $cachesizes ; do
      #[ $(( $cachesize / $recordcount )) -eq 1 ] && readonly="true" || readonly=$defaultreadonly
      #[ $(( $cachesize / $recordcount )) -eq 1 ] && operationcount="30000000"
      preload=$defaultpreload
      readonly=$defaultreadonly
      sed -e 's/size=\".*\"/size=\"'"${cachesize}"'\"/g' -i $ISPN_CFG
      #sed -e 's/read-only=\"true\"/read-only=\"'"${readonly}"'\"/g' \
      #    -e 's/read-only=\"false\"/read-only=\"'"${readonly}"'\"/g' -i $ISPN_CFG
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
              -p requestdistribution=$distribution\
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
