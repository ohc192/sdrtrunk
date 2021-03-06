/*******************************************************************************
 * sdr-trunk
 * Copyright (C) 2014-2018 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by  the Free Software Foundation, either version 3 of the License, or  (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,  but WITHOUT ANY WARRANTY; without even the implied
 * warranty of  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License  along with this program.
 * If not, see <http://www.gnu.org/licenses/>
 *
 ******************************************************************************/
package io.github.dsheirer.gui.instrument.chart;

import io.github.dsheirer.dsp.afsk.AFSK1200DecoderInstrumented;
import io.github.dsheirer.message.Message;
import io.github.dsheirer.sample.Listener;
import javafx.beans.property.SimpleIntegerProperty;

public interface IInstrumentedAFSK1200Decoder
{
    public SimpleIntegerProperty getBufferCountProperty();

    public AFSK1200DecoderInstrumented getAFSK1200Decoder();

    public void setMessageListener(Listener<Message> listener);
}
