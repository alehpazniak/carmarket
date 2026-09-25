import {useState} from 'react';
import {Loader2, MessageCircle, Phone} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {getUserProfile} from '../api/users';
import {ChatWindow} from './ChatWindow';

interface ContactSellerButtonProps {
    carId: string;
    sellerId: string;
}

/** undefined = not fetched yet, null = seller has no phone number. */
type PhoneState = string | null | undefined;

export function ContactSellerButton({carId, sellerId}: ContactSellerButtonProps) {
    const {user, isAuthenticated, loginWithGoogle} = useAuth();
    const [open, setOpen] = useState(false);
    const [phone, setPhone] = useState<PhoneState>(undefined);
    const [loadingPhone, setLoadingPhone] = useState(false);

    // Don't let the seller message themselves
    if (user?.id === sellerId) return null;

    const showPhone = async () => {
        // Phone numbers are for logged-in users only; login returns to this page.
        if (!isAuthenticated) {
            loginWithGoogle();
            return;
        }
        setLoadingPhone(true);
        try {
            const seller = await getUserProfile(sellerId);
            setPhone(seller.phoneNumber?.trim() || null);
        } catch (err) {
            console.error(err);
            alert('Nie udało się pobrać numeru telefonu');
        } finally {
            setLoadingPhone(false);
        }
    };

    const callButton = phone ? (
        <a className="contact-btn-outline" href={`tel:${phone.replace(/[^\d+]/g, '')}`}>
            <Phone size={18}/> {phone}
        </a>
    ) : phone === null ? (
        <span className="contact-phone-missing">Sprzedający nie podał numeru telefonu</span>
    ) : (
        <button className="contact-btn-outline" onClick={showPhone} disabled={loadingPhone}>
            {loadingPhone ? <Loader2 size={18} className="animate-spin"/> : <Phone size={18}/>} Zadzwoń
        </button>
    );

    if (!isAuthenticated) {
        return (
            <div className="contact-actions">
                <button className="contact-btn" onClick={loginWithGoogle}>
                    <MessageCircle size={18}/> Napisz
                </button>
                {callButton}
            </div>
        );
    }

    return (
        <div className="contact-seller">
            <div className="contact-actions">
                {!open && (
                    <button className="contact-btn" onClick={() => setOpen(true)}>
                        <MessageCircle size={18}/> Napisz
                    </button>
                )}
                {callButton}
            </div>
            {open && (
                <div className="contact-modal">
                    <div className="contact-modal-header">
                        <span>Chat with seller</span>
                        <button onClick={() => setOpen(false)}>✕</button>
                    </div>
                    <ChatWindow carId={carId} sellerId={sellerId}/>
                </div>
            )}
        </div>
    );
}
