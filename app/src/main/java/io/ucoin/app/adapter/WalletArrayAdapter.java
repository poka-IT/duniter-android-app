package io.ucoin.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.ucoin.app.R;
import io.ucoin.app.model.Wallet;

public class WalletArrayAdapter extends ArrayAdapter<Wallet> {

    private static int DEFAULT_LAYOUT_RES = R.layout.list_item_wallet;
    private int mResource;
    private int mDropDownResource;

    public WalletArrayAdapter(Context context) {
        this(context, new ArrayList<Wallet>());
    }

    public WalletArrayAdapter(Context context, List<Wallet> wallets) {
        this(context, DEFAULT_LAYOUT_RES, wallets);
    }

    public WalletArrayAdapter(Context context, int resource) {
        super(context, resource);
        mResource = resource;
        mDropDownResource = resource;
        setDropDownViewResource(resource);
    }

    public WalletArrayAdapter(Context context, int resource, List<Wallet> wallets) {
        super(context, resource, wallets);
        mResource = resource;
        mDropDownResource = resource;
        setDropDownViewResource(resource);
    }

    @Override
    public void setDropDownViewResource(int resource) {
        super.setDropDownViewResource(resource);
        mDropDownResource = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup container) {
        if (mResource != DEFAULT_LAYOUT_RES) {
            return super.getView(position, convertView, container);
        }
        return computeView(position, convertView, container, mResource);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup container) {
        if (mDropDownResource != DEFAULT_LAYOUT_RES) {
            return super.getDropDownView(position, convertView, container);
        }
        return computeView(position, convertView, container, mDropDownResource);
    }

    public void setError(View v, CharSequence s) {
        ViewHolder viewHolder = (ViewHolder) v.getTag();
        if (viewHolder == null) {
            viewHolder.name = (TextView) v.findViewById(R.id.name);
        }
        viewHolder.name.setError(s);
    }

    /* -- internal method -- */

    protected View computeView(int position, View convertView, ViewGroup container, int resource) {

        // Retrieve the item
        Wallet wallet = getItem(position);
        ViewHolder viewHolder;

        //inflate
        if (convertView == null) {
            convertView = LayoutInflater.from(this.getContext())
                    .inflate(resource, container, false);
            viewHolder = new ViewHolder();
            viewHolder.name = (TextView) convertView.findViewById(R.id.name);
            viewHolder.pubkey = (TextView) convertView.findViewById(R.id.pubkey);
            viewHolder.credit = (TextView) convertView.findViewById(R.id.credit);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // Name
        viewHolder.name.setText(wallet.getName());

        // pubKey
        viewHolder.pubkey.setText(wallet.getPubKeyHash());

        // Credit
        viewHolder.credit.setText(
                getContext().getString(R.string.credit,
                        wallet.getCredit()));

        return convertView;
    }

    // View lookup cache
    private static class ViewHolder {
        TextView name;
        TextView credit;
        TextView pubkey;
    }
}