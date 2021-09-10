numa_node=${NUMA_NODE:-0}
jheap_size=${JHEAP_SIZE:-20g}
export JAVA_HOME=/home/anatole/jdk8u/build/linux-x86_64-normal-server-release/jdk
export JAVA_OPTS="-Xmx${jheap_size} -XX:+UseG1GC" #-agentlib:hprof=cpu=samples"
#export JAVA_OPTS="-Xmx100g -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=15" #-agentlib:hprof=cpu=samples"
#export JAVA_OPTS="-Xmx100g -XX:+UseG1GC -XX:+UseNUMA -XX:InitiatingHeapOccupancyPercent=0" #-agentlib:hprof=cpu=samples"
PIN_CPU="numactl -N $numa_node --"
#-XX:-UseCompressedOops -XX:+UseNUMR
#numactl -i all -N 0 --
#numactl -p 0 -N 0 --

OUTDIR="./out"
EXPDIR=$OUTDIR/exp8.pdt.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data
mkdir -p $EXPDIR $LOGDIR $DATADIR

YCSB_DIR=..

bindings="jnvm-blackhole jnvm-blackhole-offheap jnvm-vhmap jnvm-vtmap jnvm-vslmap jnvm-rhmap jnvm-rtmap jnvm-rslmap"
recordcounts="1000000"
minoperationcount=3000000
defaultfieldcount="10"
fieldcounts="10"
defaultfieldlength="100"
fieldlengths="100"
workloads="workloada"
distribution="zipfian"
threads="1"
ycsb_jobs="run"
dataintegrity="true" #false"
#dataintegrity="true" #false"
#ycsb_preload=""
ycsb_preload="-preload"

n_run=1
#n_run=6
loadcachesize="1"
cachesize="1"
m="default"
p="default"
fs="none"
readonly="false"
preload="true"

for binding in $bindings ; do
  offheap=false
  #ycsb_preload=""
  case $binding in
    jnvm-blackhole)
      offheap=false
      #ycsb_preload="-preload"
      ;;
    jnvm-blackhole-offheap)
      offheap=true
      #ycsb_preload="-preload"
      ;;
    jnvm-v*)
      offheap=false
      #ycsb_preload="-preload"
      ;;
    jnvm-r*)
      offheap=true
      #ycsb_preload=""
      ;;
    *)
      echo "Unsupported YCSB binding for this experiment" && exit 1
      ;;
  esac
  for fieldcount in $fieldcounts ; do
  for fieldlength in $fieldlengths ; do
  for recordcount in $recordcounts ; do
    #recordcount=$(( $recordcount * $defaultfieldlength / $fieldlength ))
    [ $recordcount -lt $minoperationcount ] && operationcount=$minoperationcount\
                                            || operationcount=$recordcount
    #YCSB LOAD, once per record/field count and binding, with default parameters
    rm -fr /pmem{0,1,2,3}/* /dev/shm/* /blackhole/*
    if [ -z $ycsb_preload ] ; then
      $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh load $binding -P ${YCSB_DIR}/workloads/workloada -threads 1\
        -p dataintegrity=$dataintegrity\
        -p offheap=$offheap\
        -p recordcount=$recordcount\
        -p operationcount=$operationcount\
        -p fieldcount=$fieldcount\
        -p fieldlength=$fieldlength\
        -p requestdistribution=$distribution\
        -p measurement.trackjvm=true\
        -p measurementtype=hdrhistogram\
        -p hdrhistogram.output.path=$LOGDIR/$binding.load.workloada.$dataintegrity.$recordcount.$loadcachesize.$fieldcount.$distribution.1.hdr.log\
        >> $LOGDIR/$binding.load.workloada.$dataintegrity.$recordcount.$loadcachesize.$fieldcount.$distribution.1.log
    fi

    #YCSB RUN parameter loop
    for thread in $threads ; do
      for workload in $workloads ; do
        for integrity in $dataintegrity ; do
          for ycsb_job in $ycsb_jobs ; do
          for i in `seq 1 $n_run` ; do
            [ ! -z $ycsb_preload ] && rm -fr /pmem{0,1,2,3}/* /dev/shm/* /blackhole/*  
            $PIN_CPU ${YCSB_DIR}/bin/ycsb.sh $ycsb_job $binding -P ${YCSB_DIR}/workloads/$workload $ycsb_preload -s -threads $thread\
              -p dataintegrity=$integrity\
              -p offheap=$offheap\
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
