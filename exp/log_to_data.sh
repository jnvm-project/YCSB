#!/bin/bash

[ $# -ge 1 ] && experiments="$@"\
             || experiments="exp00.heapsize exp0.exectime exp1.cachesize exp2.keycount exp4.objsize exp5.concurrent exp6.fieldcount exp7.marshalling exp8.pdt"
             #|| experiments="exp00.heapsize exp0.exectime exp1.cachesize exp2.keycount exp3.distribution exp4.objsize exp5.concurrent exp6.fieldcount exp7.marshalling exp8.pdt"

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
    fieldlength=`echo $logfile | cut -d "." -f8`
    distribution=`echo $logfile | cut -d "." -f9`
    threads=`echo $logfile | cut -d "." -f10`
    readonly=`echo $logfile | cut -d "." -f11`
    preload=`echo $logfile | cut -d "." -f12`
    memory=`echo $logfile | cut -d "." -f13`
    oop=`echo $logfile | cut -d "." -f14`
    filesystem=`echo $logfile | cut -d "." -f15`
    n_run=`echo $logfile | cut -d "." -f16`
    operationcount=`sed -n -e 's/.*\ operationcount\=\(.*\)\ -p\ fieldcount.*/\1/gp' $logfilepath`
    heapsize=`sed -n -e 's/.*\ -Xmx\(.*\)\ -XX:+UseG1GC.*/\1/gp' $logfilepath | sed 's/g/GB/'`
    hinitoccper=`sed -n -e 's/.*\ -XX:InitiatingHeapOccupancyPercent=\(.*\)\ -classpath.*/\1/gp' $logfilepath`
    [ -z $hinitoccper ] && hinitoccper="40"

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
                    *"Time(ms)"*)
                        operation=`echo ${col1:1:-1} | sed 's/\%/ms/' | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            "[INSERT]"|"[READ]"|"[UPDATE]"|"[READ-MODIFY-WRITE]"|"[VERIFY]")
                case $col2 in
                    *"AverageLatency"*)
                        operation=`echo ${col1:1:-1}_lat | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *"Operations"*)
                        operation=`echo ${col1:1:-1}_ops | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            "[LOAD]"|"[TRANSACTION]"|"[INIT]"|"[CLEANUP]")
                case $col2 in
                    *"MaxLatency"*)
                        operation=`echo ${col1:1:-1} | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            "[LOAD_GCs_"*"]"|"[MAIN_GCs_"*"]")
                case $col2 in
                    *"MaxLatency"*)
                        operation=`echo ${col1:1:-1} | sed -e 's/GCs/GC/' -e 's/(ms)/_ms/' | tr '[:upper:]' '[:lower:]'`
                        value="$col3"
                        ;;
                    *)
                        ;;
                esac
                ;;
            "[USED_MEM_MB]")
                case $col2 in
                    *"MinLatency"*|*"MaxLatency"*)
                        operation=`echo ${col1:1:-1}_${col2:0:3} | tr '[:upper:]' '[:lower:]'`
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

        datafile="$experiment.dat"
        dataheader="#cachesize,recordcount,fieldcount,fieldlength,operationcount,threads,binding,workload,integrity,distribution,readonly,preload,memory,oop,filesystem,heapsize,hinitoccper,n_run,operation,value"
        dataline="$cachesize,$recordcount,$fieldcount,$fieldlength,$operationcount,$threads,$binding,${workload}_${ycsb_job},$integrity,$distribution,$readonly,$preload,$memory,$oop,$filesystem,$heapsize,$hinitoccper,$n_run,$operation,$value"

        datafilepath="${DATADIR}/${datafile}"

        [ ! -s $datafilepath ] && echo $dataheader > $datafilepath
        echo $dataline >> $datafilepath

    done < $logfilepath

done

done
