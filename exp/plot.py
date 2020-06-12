import os
#import pdb
import matplotlib
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

experiments=["exp1.cachesize","exp2.keycount","exp3.distribution","exp4.objsize"]
"""
datafile="./out/exp1.cachesize.ref/data/exp1.cachesize.dat"
df = pd.read_csv(datafile)
df["cacheprop"] = round(df["#cachesize"] / df["recordcount"], 1)
sns.catplot(x="cacheprop", y="value", hue="binding", col="operation", row="workload", kind="bar", sharex=False, sharey=False, data=df)
plt.savefig("./out/exp1.cachesize.png")
plt.close()

datafile="./out/exp2.keycount.ref/data/exp2.keycount.dat"
df = pd.read_csv(datafile)
sns.catplot(x="recordcount", y="value", hue="binding", col="operation", row="workload", kind="bar", sharex=False, sharey=False, data=df)
plt.savefig("./out/exp2.keycount.png")
plt.close()

datafile="./out/exp3.distribution.ref/data/exp3.distribution.dat"
df = pd.read_csv(datafile)
sns.catplot(x="fieldcount", y="value", hue="binding", col="operation", row="workload", kind="bar", sharex=False, sharey=False, data=df)
plt.savefig("./out/exp3.distribution.png")
plt.close()

datafile="./out/exp4.objsize.ref/data/exp4.objsize.dat"
df = pd.read_csv(datafile)
sns.catplot(x="fieldcount", y="value", hue="binding", col="operation", row="workload", kind="bar", sharex=False, sharey=False, data=df)
plt.savefig("./out/exp4.objsize.png")
plt.close()
"""
datafile="./out/exp5.concurrent.ref/data/exp5.concurrent.dat"
df = pd.read_csv(datafile)
df["cacheprop"] = round(df["#cachesize"] / df["recordcount"], 1)
df["binding"].loc[ df["cacheprop"] == 0.1 ] = 'infinispan10%'
df["binding"].loc[ df["cacheprop"] == 1.0 ] = 'infinispan100%'
sns.catplot(x="threads", y="value", hue="binding", col="operation", row="workload", kind="bar", sharex=False, sharey=False, data=df)
plt.savefig("./out/exp5.concurrent.png")
plt.close()
