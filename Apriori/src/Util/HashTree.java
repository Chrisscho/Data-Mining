package Util;


import org.omg.CORBA.Object;

import java.util.*;


/**
 * @author Chrisscho
 *
 * 针对Apriori算法的哈希树实现
 *
 */
public class HashTree<E extends Object&Comparable>{
    private static final int BRANCH_NUM=7;
    private int K_size=0;
    private int Max_items=3;
    private HashTreeEntry root=null;

    /**
     *
     * @param candidates 候选集的列表，根据该列表，构建哈希树
     * @param max_items 哈希树叶子节点的候选集表最大项数，超过该值，候选集表需要分裂
     */
    public HashTree(List<TreeSet<E>> candidates,int max_items){

        //候选集不为空指针
        //候选集不为空
        //候选集内项集不为空
        if(candidates!=null&&!candidates.isEmpty()&&!candidates.get(0).isEmpty()){
            root=new HashTreeEntry(0,false);
            K_size=candidates.get(0).size();
            Max_items=max_items;

            //迭代获取候选项集，插入到哈希树
            Iterator<TreeSet<E>> it=candidates.iterator();
            while(it.hasNext()){
                TreeSet<E> candidate=it.next();
                List<E> candidateList=new ArrayList<>(candidate);
                E e=candidateList.get(0);
                int hashCode=hashCode(e);

                candidateList=null;//gc友好一下

                if(root.branch[hashCode]==null)
                    root.branch[hashCode]=new HashTreeEntry(1,true);
                insertByK(candidate,1,root.branch[hashCode]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void putTransaction(Set<E> transaction){

        ArrayList<E> transactionList=new ArrayList<>(transaction);
        //已考虑的元素集合，初始为空
        List<E> preSubList=new ArrayList<>();
        //未考虑的元素集合，浅拷贝事务集，初始为整个事务集
        List<E> postSubList=(ArrayList<E>)transactionList.clone();

        seekByK(postSubList,preSubList,1,root);

        transactionList=null;
        preSubList=null;
        postSubList=null;
    }

    /**
     * 获得所有的候选项与支持度的表
     * @return Map<TreeSet<E>,Integer>
     */
    public Map<TreeSet<E>,Integer> getAllSupport(){
        Map<TreeSet<E>,Integer> supportTable=new HashMap<>();
        seekLeafByMin(root,supportTable,0);
        return supportTable;
    }

    public Map<TreeSet<E>,Integer> getSupportByMin(int min_sup){
        Map<TreeSet<E>,Integer> supportTable=new HashMap<>();
        seekLeafByMin(root,supportTable,min_sup);
        return supportTable;
    }

    /**
     * 递归遍历哈希树，找到叶子节点的候选项表，并将表项依次加入容器
     * @param handle 哈希树节点的句柄
     * @param mapContainer 项集与其支持度的entry容器，每找到一个候选项表，将其中的entry依次添加至该容器
     */

    @SuppressWarnings("unchecked")
    private void seekLeafByMin(HashTreeEntry handle,Map<TreeSet<E>,Integer> mapContainer,int min_sup){
        if(handle.isLeaf()&&handle.itemSetTable!=null){
            Map<TreeSet<E>,Integer> map=handle.getItemSetTable();
            Iterator<Map.Entry<TreeSet<E>,Integer>> it=map.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<TreeSet<E>,Integer> entry=it.next();
                TreeSet<E> itemSet=entry.getKey();
                Integer support=entry.getValue();
                //如果该项的支持度大于等于最小支持度，则添加到该容器中
                if(support>=min_sup)
                    mapContainer.put(itemSet,support);
            }
        }
        else if(!handle.isLeaf()&&handle.branch!=null){
            HashTreeEntry[] branch=handle.getBranch();
            for(int i=0;i<HashTree.BRANCH_NUM;i++){
                if(branch[i]!=null){
                    seekLeafByMin(branch[i],mapContainer,min_sup);
                }
            }
        }
    }

    /**
     *
     * @param postSubList 事务集中未被扫描的元素列表，从中获取元素，作为preSubList第k项
     *                    注意：postSubList使用了subList方法，所以不能对列表进行修改
     * @param preSubList 已扫描过的元素列表，从postSubList获取第k项
     * @param k 事务集正试图获取第k项，即哈希树到达第k-1层，k等于preSubList.size()+1
     * @param handle 哈希树句柄，将生成子集的第k项，哈希到对应的节点
     */
    private void seekByK(List<E> postSubList,List<E> preSubList,int k,HashTreeEntry handle){
        if(k<=K_size) {
            int obtained_Num = k - 1;
            int required_Num = K_size - obtained_Num;
            int first = 1;
            int last = postSubList.size() - required_Num + 1;


            for (int i = first; i <= last; i++) {
                //考虑postSubList中的第0个元素
                //作为生成子集的第k项
                E e = postSubList.get(0);
                //从第1个元素到最后一个元素，截取为未考虑的元素列表
                postSubList = postSubList.subList(1, postSubList.size());
                int hashCode = hashCode(e);

                //对应分支不为空
                if (handle.branch[hashCode] != null) {
                    //对应分支为叶子节点，将e作为第k项，添加到生成子集中，并查找更新候选项表
                    //将e出栈，以便考虑下一个元素，作为第k项
                    if (handle.branch[hashCode].isLeaf()) {
                        preSubList.add(e);
                        //利用preSubList和postSubList，生成k项集，调用findAndUpdate，查找更新候选项表
                        checkTable(preSubList, postSubList, handle);
                        preSubList.remove(e);
                    }
                    //对应分支为非叶子节点，将e作为第k项，添加到生成子集中，递归到下一层，获取第k+1项
                    //将e出栈，以便考虑下一个元素，作为第k项
                    else {
                        preSubList.add(e);
                        seekByK(postSubList, preSubList, k + 1, handle.branch[hashCode]);
                        preSubList.remove(e);
                    }
                }
                //对应分支为空，说明将该元素作为第k项，没有匹配的候选集，考虑下一个元素
            }
        }
    }

    private void checkTable(List<E> preSubList,List<E> postSubList,HashTreeEntry leafHandle){
        List<E> srcList=new ArrayList<>(postSubList);
        List<E> destList=new ArrayList<>(preSubList);
        genListAndCheck(srcList,destList,K_size,leafHandle);
        srcList=null;
        destList=null;
    }

    /**
     * 递归生成K项子集，并在候选项表中检查是否存在该子集
     * @param srcList 待考察元素列表
     * @param destList 目标元素列表，最终生成子集
     * @param k 子集大小
     * @param leafHandle 候选项表所在的叶子节点的句柄
     */
    @SuppressWarnings("unchecked")
    private void genListAndCheck(List<E> srcList,List<E> destList,int k,HashTreeEntry leafHandle){
        if(destList.size()==k){
            TreeSet<E> itemSet=new TreeSet<>(destList);
            leafHandle.findAndUpdate(itemSet);
            itemSet=null;//gc友好
        }
        else {
            int first = 0;
            int last = srcList.size() + destList.size() + k;

            for (int i = first; i <= last; i++) {
                E e = srcList.get(i);
                List<E> srcList_next = srcList.subList(i + 1, srcList.size());
                destList.add(e);
                genListAndCheck(srcList_next, destList, k, leafHandle);
                destList.remove(e);
            }
        }
    }

    /**
     *
     * @param candidate 候选集，将该集合插入到哈希树叶子节点
     * @param k 哈希树到达第k-1层，利用候选集第k项进行哈希，生成第k层
     * @param handle 哈希树句柄，对哈希树节点的引用
     */

    @SuppressWarnings("unchecked")
    private void insertByK(TreeSet<E> candidate,int k,HashTreeEntry handle){


        //第k层节点为叶子节点,存在候选项表
        if(handle.isLeaf()&&handle.getItemSetTable()!=null){

            //最后一个可供哈希的关键字已被用完，插入候选集表
            if(k==K_size){
                handle.addItemSet(candidate);
                return;
            }
            //候选项表未满，插入候选项表
            else if(handle.getItemsSize()<Max_items){
                handle.addItemSet(candidate);
                return;
            }

            //候选项表已满且可利用第k+1个关键字哈希
            else{
                //摘下候选项，handle变为非叶子节点，生成分支数组
                Set<TreeSet<E>> table=handle.removeItemSetTable();
                if(table!=null) {
                    table.add(candidate);

                    for (TreeSet<E> itemSet : table) {

                        List<E> itemList = new ArrayList<>(itemSet);
                        //获得第k+1个关键字并哈希
                        E e = itemList.get(k + 1);
                        int hashCode = hashCode(e);

                        itemList = null;//gc友好

                        //将候选项插入对应的分支节点
                        if (handle.branch[hashCode] == null)
                            handle.branch[hashCode] = new HashTreeEntry(k + 1, true);
                        insertByK(itemSet, k + 1, handle.branch[hashCode]);
                    }
                }
            }
        }

        //第k层节点为非叶子节点，将第k+1个关键字哈希，插入对应分支
        else if(!handle.isLeaf()&&handle.branch!=null){
            List<E> itemList=new ArrayList<>(candidate);
            E e=itemList.get(k+1);
            int hashCode=hashCode(e);

            itemList=null;//gc友好

            //如果第k+1层对应节点为空，建立分支
            if(handle.branch[hashCode]==null){
                handle.branch[hashCode]=new HashTreeEntry(k+1,true);
            }
            insertByK(candidate,k+1,handle.branch[hashCode]);
        }
    }


    private int hashCode(E key){
        return key.hashCode()%(HashTree.BRANCH_NUM);
    }


    static final class HashTreeEntry<E extends Object&Comparable>{

        private int level;//该节点所处层次
        private boolean leafFlag;//叶节点标志

        private HashTreeEntry[] branch=null;//分支数组
        private Map<TreeSet<E>,Integer> itemSetTable=null;//侯选项集及其支持度计数


        HashTreeEntry(int level,boolean isLeaf){
            this.level=level;
            this.leafFlag=isLeaf;

            /*
            * 如果是非叶子节点，则建立新的分支
             */
            if(!isLeaf){
                branch=new HashTreeEntry[HashTree.BRANCH_NUM];
                for(int i=0;i<HashTree.BRANCH_NUM;i++)
                    branch[i]=null;
            }else
                itemSetTable=new HashMap<>();
        }

        boolean isLeaf(){
            return this.leafFlag;
        }

        int getLevel(){ return this.level;}

        /*
        * 添加新侯选项集
        * 如果是叶子节点，则添加该候选项集，返回true
        * 否则返回false
         */
        boolean addItemSet(TreeSet<E> newItemSet){
            //叶子节点才有添加候选集表
            if(isLeaf()){
                if(itemSetTable==null)
                    itemSetTable=new HashMap<>();
                itemSetTable.put(newItemSet,0);
                return true;
            }
            //非叶子节点没有候选集表
            else
                return false;
        }

        Map<TreeSet<E>,Integer> getItemSetTable(){
            return this.itemSetTable;
        }

        int getItemsSize(){
            if(itemSetTable!=null){
                return itemSetTable.size();
            }else return -1;
        }

        HashTreeEntry[] getBranch(){
            return this.branch;
        }
        /*
        * 当新增侯选项集时，在k层出现冲突时，去掉候选集表
        * 新建分支节点，并将冲突的项集根据k+1项的哈希值，插入到第k+1层
        *
        * 去掉候选集表，返回候选集表Map，并设置该节点为非叶子节点
         */
        Set<TreeSet<E>> removeItemSetTable(){
            Set<TreeSet<E>> removedItemSets;
            //叶子节点才有候选集表
            if(isLeaf()){
                removedItemSets=this.itemSetTable.keySet();
                this.itemSetTable=null;

                //设置节点为非叶子节点
                this.leafFlag=false;
                //构造分支数组
                branch=new HashTreeEntry[HashTree.BRANCH_NUM];
                for(int i=0;i<HashTree.BRANCH_NUM;i++)
                    branch[i]=null;
                return removedItemSets;
            }
            return null;
        }

        /*
        * 判断候选集表中是否包含该项集
        * 如果包含，则增加该候选集支持度计数，返回true
        * 否则，返回false
         */
        void findAndUpdate(TreeSet<E> itemSet){

            //有候选集表，进行查找更新
            if(itemSetTable!=null) {
                //迭代该结点候选集表，获取单个候选集
                Iterator<Map.Entry<TreeSet<E>, Integer>> it = itemSetTable.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<TreeSet<E>, Integer> entry = it.next();
                    TreeSet<E> candidateSet = entry.getKey();

                    //比较候选集与该项集
                    //相同则增加支持度计数，返回true
                    if (candidateSet.equals(itemSet)) {
                        int support = entry.getValue() + 1;
                        entry.setValue(support);
                    }
                }
            }
        }
    }
}
