#!/bin/bash

experiments="exp0.exectime exp1.cachesize exp2.keycount exp3.distribution exp4.objsize exp5.concurrent exp6.fieldcount"

for experiment in $experiments ; do

OUTDIR="./out"
EXPDIR=$OUTDIR/$experiment.ref
LOGDIR=$EXPDIR/log
DATADIR=$EXPDIR/data

for logfilepath in $LOGDIR/*.log ; do
    logfile=`basename $logfilepath`

    binding=`echo $logfile | cut -d "." -f1`
    ycsb_job=`echo $logfile | cut -d "." -f2`
    workload=`echo $logfile | cut -d "." -f3`
    integrity=`echo $logfile | cut -d "." -f4`
    recordcount=`echo $logfile | cut -d "." -f5`
    cachesize=`echo $logfile | cut -d "." -f6`
    fieldcount=`echo $logfile | cut -d "." -f7`
    distribution=`echo $logfile | cut -d "." -f8`
    threads=`echo $logfile | cut -d "." -f9`
    operationcount=`sed -e 's/.*\ operationcount\=\(.*\)\ -p\ fieldcount.*/\1/gp' $logfilepath | head -n 1`

    while IFS=", " read col1 col2 col3 ; do
        operation=""
        value=""
        case $col1 in
            "[OVERALL]")
                case $col2 in
                    *"RunTime"*|*"Throughput"*)
                        operation=`echo $col2 | cut -d "(" -f1 | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            "[TOTAL_GC_TIME_%]")
                case $col2 in
                    *"Time(%)"*)
                        operation=`echo ${col1:1:-1} | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            "[INIT]"|"[CLEANUP]"|"[INSERT]"|"[READ]"|"[UPDATE]"|"[VERIFY]")
                case $col2 in
                    *"AverageLatency"*)
                        operation=`echo ${col1:1:-1} | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            "[LOAD]"|"[TRANSACTION]")
                case $col2 in
                    *"MaxLatency"*)
                        operation=`echo ${col1:1:-1} | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            *)
                ;;
        esac

        [ -z $operation ] && continue
        [ -z $value ] && continue

        #datafile="$binding.$workload.$ycsb_job.$integrity.$distribution.$operation.dat"
        #dataheader="#cachesize,recordcount,fieldcount,threads,value"
        #dataline="$cachesize,$recordcount,$fieldcount,$threads,$value"

        datafile="$experiment.dat"
        dataheader="#cachesize,recordcount,fieldcount,operationcount,threads,binding,workload,integrity,distribution,operation,value"
        dataline="$cachesize,$recordcount,$fieldcount,$operationcount,$threads,$binding,${workload}_${ycsb_job},$integrity,$distribution,$operation,$value"

        datafilepath="${DATADIR}/${datafile}"

        [ ! -s $datafilepath ] && echo $dataheader > $datafilepath
        echo $dataline >> $datafilepath

    done < $logfilepath

done

done
