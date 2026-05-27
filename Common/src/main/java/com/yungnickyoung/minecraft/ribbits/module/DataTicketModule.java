package com.yungnickyoung.minecraft.ribbits.module;

import com.yungnickyoung.minecraft.ribbits.data.RibbitData;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;

public class DataTicketModule {
    public static final DataTicket<RibbitData> DT_RIBBIT_DATA =
            DataTickets.create("ribbit_data", RibbitData.class);
    public static final DataTicket<Boolean> DT_PLAYING_INSTRUMENT =
            DataTickets.create("ribbit_playing_instrument", Boolean.class);
    public static final DataTicket<Boolean> DT_UMBRELLA_FALLING =
            DataTickets.create("ribbit_umbrella_falling", Boolean.class);
    public static final DataTicket<Boolean> DT_IN_RAIN =
            DataTickets.create("ribbit_in_rain", Boolean.class);
    public static final DataTicket<Boolean> DT_IS_PRIDE_RIBBIT =
            DataTickets.create("ribbit_is_pride", Boolean.class);
}
