import Util.HashTree;
import com.sun.istack.internal.NotNull;

import java.util.*;


/**
 *
 * @author Chrisscho
 *
 * Apriori算法实现
 * 1.使用散列计数，压缩侯选2项集
 * 2.使用哈希树，对k项集进行支持度计数
 */

public class Apriori<E> {

    private static double MIN_SUP=0.02;//minimum support
    private static double MIN_CONF=0.6;//minimum confidence
    private static int BARREL_NUM=7;
    private int times;//times of iteration
    private int min_sup;
    private int min_conf;

    private int[] barrel=new int[BARREL_NUM];

    private Set<Set<E>> D,L;


    private Apriori(Set<Set<E>> d,int max_times){
        this.D=d;
        this.times=max_times;
        L=new HashSet<>();
        onData(d);
        for(int i=0;i<BARREL_NUM;i++){
            barrel[i]=0;
        }
    }


    public static void main(String[] args){

        //TODO:从数据获取事务集D，并导入到D
        Set<Set<String>> D=new HashSet<>();
        Set<Set<String>> L;


        Apriori<String> apriori=new Apriori<>(D,17);

        //获得D中的频繁项集L
        L=apriori.apriori();

    }

    @SuppressWarnings("unchecked")
    private Set<Set<E>> apriori(){
        HashTree eHashTree;
        Set<E> L1=find_frequent_1_itemSets(this.D);
        //生成侯选2-项集
        Set<Set<E>> Ck=find_candidate_2_itemSets(L1);
        Set<Set<E>> Lk;
        List<TreeSet<E>> Ck_list;

        int k=2;
        do{
            //将侯选k-项集转换为有序TreeSet的列表
            Ck_list=setToListOfTreeSet(Ck);
            //初始化哈希树，候选项表最大表项数为7
            eHashTree=new HashTree(Ck_list,7);

            //扫描事务集D，利用哈希树计数
            for(Set<E> t:D)
                eHashTree.putTransaction(t);

            //从哈希树获取频繁k-项集，最小支持度为min_sup
            Lk=eHashTree.getSupportByMin(this.min_sup).keySet();

            //如果Lk为空，即不存在k-项的频繁项集，结束
            if(Lk.isEmpty())
                break;

            //将频繁k-项集并入频繁项集L
            L.addAll(Lk);
            //由频繁k-项集生成侯选k+1-项集
            Ck=apriori_gen(k,Lk);
            k++;
            eHashTree=null;

        }while(!Lk.isEmpty()&&k<=times);//如果无频繁k-项集或超出最大迭代次数，则结束循环
        return L;
    }

    /**
     * 将候选项集变为列表，且将候选项变为有序的TreeSet，以供哈希树使用
     * @param set 候选项集
     * @return 元素为有序TreeSet的列表
     */
    private List<TreeSet<E>> setToListOfTreeSet(Set<Set<E>> set){
        List<TreeSet<E>> candidates=new ArrayList<>();
        TreeSet<E> candidate;
        for(Set<E> eSet:set){
            candidate=new TreeSet<>(eSet);
            candidates.add(candidate);
        }
        return candidates;
    }

    /**
     * 根据事务集的大小，计算出绝对的最小支持度和最小置信度
     * @param D 事务集
     */
    private void onData(Set<Set<E>> D){
        //根据事务集大小，计算出绝对最小支持度
        int transaction_size=D.size();
        min_sup=(int)(transaction_size*Apriori.MIN_SUP);
        min_conf=(int)(transaction_size*Apriori.MIN_CONF);
    }


    /**
     * 根据事务集求出频繁1-项集，并且对每条事务集求2-项集，对哈希桶计数，以压缩2-项集的侯选集
     * @param D 事务集
     * @return 频繁1项集
     */

    private Set<E> find_frequent_1_itemSets(Set<Set<E>> D){

        Set<E> L1=new HashSet<>(),transaction;
        Map<E,Integer> frequent_1_itemMap=new HashMap<>();
        Iterator<Set<E>> it=D.iterator();

        //遍历事务集，生成1-项集，计算支持度
        while(it.hasNext()){
            //获得一条事务集
            transaction=it.next();

            //遍历事务集中的元素
            for(E e:transaction)
            {
                //如果已加入map，则增加计数
                if(frequent_1_itemMap.containsKey(e)){
                    int value=frequent_1_itemMap.get(e)+1;
                    frequent_1_itemMap.put(e,value);
                }
                //如果没有加入map，则添加新的entry
                else
                    frequent_1_itemMap.put(e,1);


                //将e之后的元素，依次与e构成二项集(e,e2)
                //并哈希到桶中，增加对应桶的计数值
                Iterator<E> laterIt=getIterator(e,transaction);
                while(laterIt.hasNext()){
                    E e2=laterIt.next();
                    int hashCode=hashBarrel(e,e2);
                    barrel[hashCode]++;
                }
            }
        }

        //遍历map，如果元素e的支持度大于等于最小支持度
        //将e添加至L1中
        Set<Map.Entry<E,Integer>> entrySet=frequent_1_itemMap.entrySet();
        for(Map.Entry<E,Integer> entry:entrySet){
            int support=entry.getValue();
            if(support>=min_sup)
                L1.add(entry.getKey());
        }

        return L1;
    }

    /**
     * 根据频繁1-项集求出侯选2-项集，并用哈希桶进行压缩
     * @param freqItemSet_1 频繁1项集
     * @return 侯选2项集
     */
    private Set<Set<E>> find_candidate_2_itemSets(Set<E> freqItemSet_1){
        Set<Set<E>> C_2=new HashSet<>();
        Set<E> temp;
        for(E e1:freqItemSet_1){
            Iterator<E> it=getIterator(e1,freqItemSet_1);
            while(it.hasNext()){
                E e2=it.next();
                int hashCode=hashBarrel(e1,e2);

                //如果(e1,e2)哈希对应的桶计数大于最小支持度
                //将(e1,e2)添加到2-项集的候选集
                //否则，根据反单调性，该2-项集一定不是频繁项集
                if(barrel[hashCode]>=min_sup){
                    temp=new HashSet<>();
                    temp.add(e1);
                    temp.add(e2);
                    C_2.add(temp);
                }
            }
        }
        return C_2;
    }

    /**
     * 哈希桶的哈希函数
     * @param x 参数x
     * @param y 参数y
     * @return 哈希值
     */
    private int hashBarrel(E x,E y){
        return (x.hashCode()*10+y.hashCode())%7;
    }
    /**
     * 根据k-项的频繁项集生成(k+1)-项的候选项集
     * @param k itemSet_k中集合的项数,k>=2
     * @param freqItemSet_k k-项的频繁项集
     * @return (k+1)-项的候选项集
     */
    private Set<Set<E>> apriori_gen(int k,Set<Set<E>> freqItemSet_k){
        Set<Set<E>> C_k_p_1=new HashSet<>();
        Iterator<Set<E>> it=freqItemSet_k.iterator();
        Set<E> baseSet=null,offSet=null;
        Iterator<Set<E>> laterIt=null;
        while(it.hasNext()){
            //baseSet为基准集，对baseSet之后的所有集合
            //依次与baseSet比较，如果前k-1项相同，第k项不同
            //合并生成k+1-项的集合
            baseSet=it.next();
            laterIt=getIterator(baseSet,freqItemSet_k);

            while(laterIt.hasNext()){
                offSet=laterIt.next();
                Set<E> temp=new HashSet<>(baseSet);
                temp.retainAll(offSet);
                //连接，如果有k-1项相同，则合并两个集合生成k+1项集
                if(temp.size()==k-1){
                    Set<E> newSet=new HashSet<>(baseSet);
                    newSet.removeAll(temp);
                    newSet.addAll(offSet);

                    //剪枝方法，检查newSet中是否出现非频繁子集
                    //如果没有出现，则将newSet添加到k+1-项候选集中
                    if(!has_infrequent_subSet(newSet,freqItemSet_k))
                        C_k_p_1.add(newSet);

                    newSet=null;//gc-friendly
                }
                temp=null;//gc-friendly
            }
        }
        return C_k_p_1;
    }

    /**
     * 检查k+1项集中是否有非频繁子集
     * @param c_k_p_1 k+1-项集
     * @param L_k 频繁k项集
     * @return boolean
     */
    private boolean has_infrequent_subSet(@NotNull Set<E> c_k_p_1, @NotNull Set<Set<E>> L_k){
        Iterator<E> c_it=c_k_p_1.iterator();
        Set<E> subSet=new HashSet<>(c_k_p_1);
        while(c_it.hasNext()){
            E e=c_it.next();
            subSet.remove(e);

            if(!L_k.contains(subSet))
                return true;
            subSet.add(e);
        }
        return false;
    }

    /**
     * 返回从某个对象开始，之后的迭代器
     * @param itemSet 对象
     * @param list 对象容器
     * @return 游标在itemSet之后的list迭代器
     */
    private Iterator<Set<E>> getIterator(@NotNull Set<E> itemSet,@NotNull Set<Set<E>> list){
        Iterator<Set<E>> it=list.iterator();
        while(it.hasNext()){
            if(itemSet.equals(it.next()))
                break;
        }
        return it;
    }

    private Iterator<E> getIterator(@NotNull E e,@NotNull Set<E> list){
        Iterator<E> it=list.iterator();
        while(it.hasNext()){
            if(e.equals(it.next()))
                break;
        }
        return it;
    }
}
